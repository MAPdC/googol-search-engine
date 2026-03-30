package dei.googol.core;

import dei.googol.rmi.IBarrel;
import dei.googol.rmi.IDownloader;
import dei.googol.rmi.IUrlQueue;
import dei.googol.rmi.IndexUpdateMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Web crawler component of the Googol search engine.
 *
 * <h2>Crawl loop</h2>
 * <p>On startup, the Downloader enters an infinite crawl loop on a background
 * thread:
 * <ol>
 *   <li>Dequeue the next URL from the shared {@link UrlQueue}.</li>
 *   <li>Fetch and parse the HTML page with <a href="https://jsoup.org/">jsoup</a>.</li>
 *   <li>Extract all words (filtering stop words and short tokens).</li>
 *   <li>Broadcast an {@link IndexUpdateMessage} for each word to <em>all</em>
 *       active Barrels — this is the <strong>reliable multicast</strong>.</li>
 *   <li>Enqueue every hyperlink found on the page for future crawling.</li>
 *   <li>Register backlinks in all Barrels so the ranking algorithm can use them.</li>
 * </ol>
 *
 * <h2>Reliable multicast</h2>
 * <p>Each {@code IndexUpdateMessage} is sent to every known Barrel via RMI
 * (one-to-many). If a Barrel is unreachable, it is removed from the active list
 * immediately so that no future messages are wasted on it. A health-check thread
 * runs every 5 seconds to rediscover Barrels that have come back online.
 *
 * <h2>Parallelism</h2>
 * <p>Running multiple Downloader instances (each as a separate JVM process) gives
 * linear crawl throughput. Each Downloader independently dequeues URLs from the
 * shared queue, so there is no duplicate work as long as the UrlQueue is the
 * single source of truth.
 */
public class Downloader extends UnicastRemoteObject implements IDownloader {

    private final String name;
    private final String rmiHost;
    private final int    rmiPort;

    /**
     * Thread-safe list of currently reachable Barrels.
     * {@link CopyOnWriteArrayList} is used so that the crawl loop can iterate
     * without holding a lock while the health-check thread modifies the list.
     */
    private final CopyOnWriteArrayList<IBarrel> barrels = new CopyOnWriteArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * Creates, registers, and starts a Downloader.
     *
     * @param name     unique name, e.g. {@code "Downloader1"}
     * @param rmiHost  hostname of the RMI Registry
     * @param rmiPort  port of the RMI Registry
     * @throws RemoteException on RMI registration failure
     */
    public Downloader(String name, String rmiHost, int rmiPort) throws RemoteException {
        super();
        this.name    = name;
        this.rmiHost = rmiHost;
        this.rmiPort = rmiPort;
        refreshBarrels();
        registerInRegistry();
        startHealthCheckThread();
        new Thread(this::crawlLoop, name + "-crawl-thread").start();
    }

    // ── IDownloader ───────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    public String getName() throws RemoteException {
        return name;
    }

    // ── Crawl loop ────────────────────────────────────────────────────────

    /**
     * Main crawl loop — runs indefinitely on a dedicated background thread.
     *
     * <p>Fetches URLs from the shared queue one at a time, parses each page, and
     * multicasts the extracted words + backlinks to all active Barrels.
     */
    private void crawlLoop() {
        System.out.printf("[%s] Crawl loop started.%n", name);
        while (true) {
            try {
                IUrlQueue urlQueue = lookupUrlQueue();
                if (urlQueue == null) {
                    sleep(2000);
                    continue;
                }

                String url = urlQueue.getNextUrl();
                if (url == null) {
                    sleep(1000); // Queue is empty — wait and retry.
                    continue;
                }

                processUrl(url, urlQueue);

            } catch (Exception e) {
                System.err.printf("[%s] Error in crawl loop: %s%n", name, e.getMessage());
                sleep(1000);
            }
        }
    }

    /**
     * Fetches, parses, and indexes a single URL.
     *
     * <ol>
     *   <li>Downloads the HTML page via jsoup.</li>
     *   <li>Extracts title, text, and hyperlinks.</li>
     *   <li>Multicasts each word as an {@link IndexUpdateMessage} to all Barrels.</li>
     *   <li>Registers backlinks in all Barrels.</li>
     *   <li>Enqueues all discovered hyperlinks for future crawling.</li>
     * </ol>
     */
    private void processUrl(String url, IUrlQueue urlQueue) {
        try {
            Document doc     = Jsoup.connect(url).timeout(8000).get();
            String   title   = doc.title();
            String   text    = doc.text();
            String[] tokens  = text.split("[^a-zA-Z0-9À-ÿ]+");
            String   snippet = buildSnippet(tokens);

            // ── Multicast each word to all Barrels ──────────────────────
            for (String token : tokens) {
                String word = token.toLowerCase().replaceAll("[^a-z0-9àáâãéêíóôõúüç]", "");
                if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                    broadcast(new IndexUpdateMessage(word, url, title, snippet));
                }
            }

            // ── Process hyperlinks ──────────────────────────────────────
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String linkedUrl = link.attr("abs:href");
                if (linkedUrl.startsWith("http")) {
                    // Register backlink in every Barrel
                    broadcastBacklink(url, linkedUrl);
                    // Schedule for crawling (UrlQueue deduplicates via Bloom filter)
                    try {
                        urlQueue.addUrl(linkedUrl);
                    } catch (RemoteException e) {
                        System.err.printf("[%s] Failed to enqueue %s: %s%n",
                                name, linkedUrl, e.getMessage());
                    }
                }
            }

            System.out.printf("[%s] Indexed: %s (%d tokens, %d links)%n",
                    name, url, tokens.length, links.size());

        } catch (IOException e) {
            System.err.printf("[%s] Failed to fetch %s: %s%n", name, url, e.getMessage());
        }
    }

    // ── Reliable multicast helpers ────────────────────────────────────────

    /**
     * Sends {@code message} to every currently known Barrel.
     * Any Barrel that throws a {@link RemoteException} is immediately removed from
     * the active list — it will be rediscovered by the health-check thread if it
     * comes back online.
     */
    private void broadcast(IndexUpdateMessage message) {
        List<IBarrel> failed = new ArrayList<>();
        for (IBarrel barrel : barrels) {
            try {
                barrel.processMessage(message);
            } catch (RemoteException e) {
                System.err.printf("[%s] Barrel unreachable, removing from list.%n", name);
                failed.add(barrel);
            }
        }
        barrels.removeAll(failed); // FIX: actually remove failed barrels
    }

    /**
     * Registers a backlink ({@code fromUrl} → {@code toUrl}) in every Barrel.
     * Failed Barrels are removed, same as in {@link #broadcast}.
     */
    private void broadcastBacklink(String fromUrl, String toUrl) {
        List<IBarrel> failed = new ArrayList<>();
        for (IBarrel barrel : barrels) {
            try {
                barrel.addBacklink(fromUrl, toUrl);
            } catch (RemoteException e) {
                failed.add(barrel);
            }
        }
        barrels.removeAll(failed);
    }

    // ── Barrel discovery & health check ──────────────────────────────────

    /**
     * Scans the RMI Registry for entries whose names start with {@code "Barrel"}
     * and replaces the current active list with the discovered ones.
     * Called at startup and periodically by the health-check thread.
     */
    private void refreshBarrels() {
        List<IBarrel> discovered = new ArrayList<>();
        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            for (String entry : registry.list()) {
                if (entry.startsWith("Barrel")) {
                    try {
                        IBarrel barrel = (IBarrel) registry.lookup(entry);
                        barrel.getName(); // Liveness check
                        discovered.add(barrel);
                    } catch (Exception e) {
                        // Barrel entry exists but is unreachable; skip it.
                    }
                }
            }
        } catch (Exception e) {
            System.err.printf("[%s] RMI Registry unreachable: %s%n", name, e.getMessage());
        }

        barrels.clear();
        barrels.addAll(discovered);
        System.out.printf("[%s] Active barrels: %d%n", name, barrels.size());
    }

    /** Starts a daemon thread that refreshes the barrel list every 5 seconds. */
    private void startHealthCheckThread() {
        Thread t = new Thread(() -> {
            while (true) {
                sleep(5000);
                refreshBarrels();
            }
        }, name + "-healthcheck-thread");
        t.setDaemon(true);
        t.start();
    }

    // ── RMI helpers ───────────────────────────────────────────────────────

    private void registerInRegistry() throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            registry.rebind(name, this);
            System.out.printf("[%s] Registered in RMI Registry at %s:%d%n", name, rmiHost, rmiPort);
        } catch (Exception e) {
            throw new RemoteException("Failed to register " + name, e);
        }
    }

    private IUrlQueue lookupUrlQueue() {
        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            return (IUrlQueue) registry.lookup("UrlQueue");
        } catch (Exception e) {
            System.err.printf("[%s] Could not reach UrlQueue: %s%n", name, e.getMessage());
            return null;
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private static String buildSnippet(String[] tokens) {
        int limit = Math.min(50, tokens.length);
        return String.join(" ", Arrays.copyOfRange(tokens, 0, limit));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // ── Stop words ────────────────────────────────────────────────────────

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "o", "e", "é", "de", "do", "da", "dos", "das", "em", "no", "na",
            "nos", "nas", "por", "para", "com", "sem", "que", "como", "mais", "mas",
            "se", "ou", "ao", "pelo", "à", "às", "aos", "um", "uma", "uns", "umas",
            "the", "and", "of", "to", "in", "on", "at", "by", "for", "with", "about",
            "as", "it", "is", "are", "was", "were", "this", "that", "these", "those",
            "be", "have", "has", "had", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "not", "from", "an", "or", "but", "if",
            "i", "you", "he", "she", "we", "they", "me", "him", "her", "us", "them",
            "my", "your", "his", "its", "our", "their"
    );

    // ── Entry point ───────────────────────────────────────────────────────

    /**
     * Starts a Downloader as a standalone process.
     *
     * <p>Arguments (positional):
     * <ol>
     *   <li>{@code name}    — downloader name, default {@code "Downloader1"}</li>
     *   <li>{@code rmiHost} — default {@code "localhost"}</li>
     *   <li>{@code rmiPort} — default {@code 1099}</li>
     * </ol>
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        String name    = args.length > 0 ? args[0] : "Downloader1";
        String rmiHost = args.length > 1 ? args[1] : "localhost";
        int    rmiPort = args.length > 2 ? Integer.parseInt(args[2]) : 1099;

        try {
            new Downloader(name, rmiHost, rmiPort);
            System.out.printf("[%s] Running.%n", name);
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[Downloader] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

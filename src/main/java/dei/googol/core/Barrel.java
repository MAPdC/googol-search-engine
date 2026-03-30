package dei.googol.core;

import dei.googol.rmi.IBarrel;
import dei.googol.rmi.IndexUpdateMessage;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Index Storage Barrel — the replicated data-store at the heart of Googol.
 *
 * <h2>Role in the architecture</h2>
 * <p>Multiple Barrel instances run in parallel across machines. They all hold an
 * identical copy of the inverted index, receiving updates from Downloaders via
 * RMI reliable multicast: every Downloader broadcasts each
 * {@link IndexUpdateMessage} to <em>all</em> known Barrels, so any single Barrel
 * failure is invisible to clients.
 *
 * <h2>Fault tolerance</h2>
 * <ul>
 *   <li><strong>Redundancy:</strong> the Gateway queries any available Barrel;
 *       if one is unreachable it tries another automatically.</li>
 *   <li><strong>Crash recovery:</strong> the Barrel periodically serialises its
 *       {@link InvertedIndex} to disk. On startup it attempts to load that
 *       snapshot so that it does not start from scratch after a restart.</li>
 * </ul>
 *
 * <h2>Stop words</h2>
 * <p>Common English (and Portuguese) words that carry no semantic value are
 * filtered out during {@link #processMessage} to keep the index compact.
 */
public class Barrel extends UnicastRemoteObject implements IBarrel {

    // ── Instance state ────────────────────────────────────────────────────

    private final String         name;
    private final String         snapshotPath;
    private       InvertedIndex  index;

    /** Total number of search requests served. */
    private final AtomicInteger searchCount       = new AtomicInteger(0);
    /** Cumulative search response time in nanoseconds. */
    private final AtomicLong    totalResponseNs   = new AtomicLong(0);
    /** Per-term search frequency counter. */
    private final ConcurrentHashMap<String, Integer> termCounts = new ConcurrentHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * Creates, recovers (if a snapshot exists), and registers a Barrel.
     *
     * @param name          unique name, e.g. {@code "Barrel1"}
     * @param rmiHost       hostname of the RMI Registry
     * @param rmiPort       port of the RMI Registry
     * @param snapshotDir   directory where index snapshots are stored
     * @param snapshotEvery how often (seconds) to save the index to disk
     * @throws RemoteException on RMI registration failure
     */
    public Barrel(String name, String rmiHost, int rmiPort,
                  String snapshotDir, int snapshotEvery) throws RemoteException {
        super();
        this.name         = name;
        this.snapshotPath = snapshotDir + File.separator + name + ".ser";
        this.index        = recoverOrCreateIndex();
        registerInRegistry(rmiHost, rmiPort);
        scheduleSnapshots(snapshotEvery);
    }

    // ── IBarrel — identity ────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    public String getName() throws RemoteException {
        return name;
    }

    // ── IBarrel — index writes ────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Stop words and single/double-character tokens are silently discarded
     * before the word is forwarded to {@link InvertedIndex#addToIndex}.
     */
    @Override
    public void processMessage(IndexUpdateMessage message) throws RemoteException {
        String word = message.getWord().toLowerCase();
        if (word.length() > 2 && !STOP_WORDS.contains(word)) {
            index.addToIndex(word, message.getUrl(), message.getTitle(), message.getSnippet());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addBacklink(String fromUrl, String toUrl) throws RemoteException {
        index.addBacklink(fromUrl, toUrl);
    }

    // ── IBarrel — index reads ─────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Records the round-trip time and increments the per-term counter so that
     * statistics are always up to date.
     */
    @Override
    public List<String> getUrlsForWord(String word) throws RemoteException {
        long start = System.nanoTime();
        searchCount.incrementAndGet();
        termCounts.merge(word.toLowerCase(), 1, Integer::sum);
        List<String> results = index.getUrlsForWord(word);
        totalResponseNs.addAndGet(System.nanoTime() - start);
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getBacklinks(String url) throws RemoteException {
        return index.getBacklinks(url);
    }

    /** {@inheritDoc} */
    @Override
    public String getPageTitle(String url) throws RemoteException {
        return index.getPageTitle(url);
    }

    /** {@inheritDoc} */
    @Override
    public String getPageSnippet(String url) throws RemoteException {
        return index.getPageSnippet(url);
    }

    // ── IBarrel — statistics ──────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    public int getIndexedWordCount() throws RemoteException {
        return index.getWordCount();
    }

    /** {@inheritDoc} */
    @Override
    public int getIndexedUrlCount() throws RemoteException {
        return index.getUrlCount();
    }

    /** {@inheritDoc} */
    @Override
    public double getAvgResponseTimeMs() throws RemoteException {
        int count = searchCount.get();
        if (count == 0) return 0.0;
        return (totalResponseNs.get() / (double) count) / 1_000_000.0;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Integer> getTopSearchTerms() throws RemoteException {
        return termCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getStatistics() throws RemoteException {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("name",               name);
        stats.put("indexedWords",       index.getWordCount());
        stats.put("indexedUrls",        index.getUrlCount());
        stats.put("searchCount",        searchCount.get());
        stats.put("avgResponseTimeMs",  getAvgResponseTimeMs());
        return stats;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Attempts to load a previously saved snapshot; falls back to a fresh index.
     */
    private InvertedIndex recoverOrCreateIndex() {
        File snapshot = new File(snapshotPath);
        if (snapshot.exists()) {
            try {
                InvertedIndex recovered = InvertedIndex.loadFromDisk(snapshotPath);
                System.out.printf("[%s] Recovered index from snapshot (%,d words, %,d URLs)%n",
                        name, recovered.getWordCount(), recovered.getUrlCount());
                return recovered;
            } catch (Exception e) {
                System.err.printf("[%s] Could not load snapshot, starting fresh: %s%n",
                        name, e.getMessage());
            }
        }
        return new InvertedIndex();
    }

    private void registerInRegistry(String rmiHost, int rmiPort) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            registry.rebind(name, this);
            System.out.printf("[%s] Registered in RMI Registry at %s:%d%n", name, rmiHost, rmiPort);
        } catch (Exception e) {
            throw new RemoteException("Failed to register " + name + " in RMI Registry", e);
        }
    }

    /**
     * Schedules a background task that periodically saves the index to disk.
     * This enables crash recovery without replaying the full crawl history.
     */
    private void scheduleSnapshots(int intervalSeconds) {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, name + "-snapshot-thread");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            try {
                index.saveToDisk(snapshotPath);
                System.out.printf("[%s] Snapshot saved to %s%n", name, snapshotPath);
            } catch (Exception e) {
                System.err.printf("[%s] Snapshot failed: %s%n", name, e.getMessage());
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    // ── Stop words ────────────────────────────────────────────────────────

    private static final Set<String> STOP_WORDS = Set.of(
            // Portuguese
            "a", "o", "e", "é", "de", "do", "da", "dos", "das", "em", "no", "na",
            "nos", "nas", "por", "para", "com", "sem", "que", "como", "mais", "mas",
            "se", "ou", "ao", "pelo", "à", "às", "aos", "um", "uma", "uns", "umas",
            // English
            "the", "and", "of", "to", "in", "on", "at", "by", "for", "with", "about",
            "as", "it", "is", "are", "was", "were", "this", "that", "these", "those",
            "be", "have", "has", "had", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "not", "from", "an", "or", "but", "if",
            "i", "you", "he", "she", "we", "they", "me", "him", "her", "us", "them",
            "my", "your", "his", "its", "our", "their"
    );

    // ── Entry point ───────────────────────────────────────────────────────

    /**
     * Starts a Barrel as a standalone process.
     *
     * <p>Arguments (positional):
     * <ol>
     *   <li>{@code name}         — barrel name, default {@code "Barrel1"}</li>
     *   <li>{@code rmiHost}      — default {@code "localhost"}</li>
     *   <li>{@code rmiPort}      — default {@code 1099}</li>
     *   <li>{@code snapshotDir}  — default {@code "./barrel-snapshots"}</li>
     *   <li>{@code snapshotSecs} — default {@code 60}</li>
     * </ol>
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        String name         = args.length > 0 ? args[0] : "Barrel1";
        String rmiHost      = args.length > 1 ? args[1] : "localhost";
        int    rmiPort      = args.length > 2 ? Integer.parseInt(args[2]) : 1099;
        String snapshotDir  = args.length > 3 ? args[3] : "./barrel-snapshots";
        int    snapshotSecs = args.length > 4 ? Integer.parseInt(args[4]) : 60;

        try {
            new Barrel(name, rmiHost, rmiPort, snapshotDir, snapshotSecs);
            System.out.printf("[%s] Running. Waiting for index updates...%n", name);
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[Barrel] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

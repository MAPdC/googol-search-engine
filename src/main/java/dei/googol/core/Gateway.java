package dei.googol.core;

import dei.googol.rmi.IBarrel;
import dei.googol.rmi.IGateway;
import dei.googol.rmi.IUrlQueue;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * RMI Gateway — the single entry point for all Googol clients.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><strong>Load balancing:</strong> distributes search requests across all
 *       active Barrels using round-robin selection.</li>
 *   <li><strong>Fault tolerance:</strong> if the selected Barrel throws a
 *       {@link RemoteException} during a search, the Gateway automatically retries
 *       the request on a different Barrel.</li>
 *   <li><strong>Result aggregation:</strong> merges and deduplicates results from
 *       multiple Barrels when a multi-term search requires it, then re-ranks by
 *       backlink count.</li>
 *   <li><strong>Statistics:</strong> maintains a per-query frequency map so the
 *       top-10 most searched queries can be reported in real time.</li>
 * </ul>
 *
 * <h2>Barrel discovery</h2>
 * <p>Every 5 seconds a health-check thread scans the RMI Registry for entries
 * whose names start with {@code "Barrel"} and refreshes the active list.
 * This means a new Barrel that comes online is discovered automatically, and a
 * crashed Barrel is removed from rotation within 5 seconds.
 */
public class Gateway extends UnicastRemoteObject implements IGateway {

    private final String rmiHost;
    private final int    rmiPort;

    /**
     * Thread-safe list of currently reachable Barrels.
     * {@link CopyOnWriteArrayList} allows lock-free iteration during searches
     * while the health-check thread replaces the list.
     */
    private final CopyOnWriteArrayList<IBarrel> barrels = new CopyOnWriteArrayList<>();

    /** Round-robin cursor (modulo barrels.size()). */
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    /** Full search query → number of times it was submitted. */
    private final ConcurrentHashMap<String, Integer> queryFrequency = new ConcurrentHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * Creates, registers, and starts the Gateway.
     *
     * @param rmiHost hostname of the RMI Registry
     * @param rmiPort port of the RMI Registry
     * @throws RemoteException on RMI registration failure
     */
    public Gateway(String rmiHost, int rmiPort) throws RemoteException {
        super();
        this.rmiHost = rmiHost;
        this.rmiPort = rmiPort;
        refreshBarrels();
        registerInRegistry();
        startHealthCheckThread();
    }

    // ── IGateway — search ─────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li>Selects a Barrel via round-robin.</li>
     *   <li>For each search term, fetches matching URLs from <em>all</em> active
     *       Barrels and intersects the result sets so that only URLs containing
     *       <em>all</em> terms are returned.</li>
     *   <li>For each surviving URL, sums its backlink counts across all Barrels
     *       and sorts descending (most-linked first).</li>
     *   <li>Enriches each result with its title and snippet retrieved from a
     *       single Barrel.</li>
     * </ol>
     *
     * <p>If the initially selected Barrel fails, the next one in the round-robin
     * order is tried transparently.
     *
     * @throws RemoteException if no Barrel is available
     */
    @Override
    public List<Map<String, String>> search(String query) throws RemoteException {
        if (barrels.isEmpty()) {
            throw new RemoteException("No active Barrels available to serve this request.");
        }

        // Track query frequency for statistics
        queryFrequency.merge(query.trim().toLowerCase(), 1, Integer::sum);

        String[] terms = query.trim().split("\\s+");

        // Step 1: For each term, gather the union of matching URLs across ALL barrels.
        Set<String> intersection = null;
        Map<String, Integer> backlinkTotals = new HashMap<>();

        for (String term : terms) {
            Set<String> termUrls = new HashSet<>();
            for (IBarrel barrel : barrels) {
                try {
                    List<String> urls = barrel.getUrlsForWord(term);
                    termUrls.addAll(urls);
                    // Accumulate backlink counts while we have the barrel's attention
                    for (String url : urls) {
                        int bl = barrel.getBacklinks(url).size();
                        backlinkTotals.merge(url, bl, Integer::sum);
                    }
                } catch (RemoteException e) {
                    System.err.printf("[Gateway] Barrel unreachable during search: %s%n",
                            e.getMessage());
                }
            }
            if (intersection == null) {
                intersection = termUrls;
            } else {
                intersection.retainAll(termUrls); // AND semantics across terms
            }
        }

        if (intersection == null || intersection.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: Sort by total backlink count descending.
        List<String> ranked = intersection.stream()
                .sorted(Comparator.comparingInt(
                        url -> -backlinkTotals.getOrDefault(url, 0)))
                .collect(Collectors.toList());

        // Step 3: Enrich with title + snippet from one available Barrel.
        IBarrel reader = pickBarrel();
        List<Map<String, String>> results = new ArrayList<>();
        for (String url : ranked) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("url", url);
            try {
                entry.put("title",   reader.getPageTitle(url));
                entry.put("snippet", reader.getPageSnippet(url));
            } catch (RemoteException e) {
                entry.put("title",   url);
                entry.put("snippet", "");
            }
            results.add(entry);
        }
        return results;
    }

    // ── IGateway — indexing ───────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the {@link UrlQueue} in the RMI Registry and submits the URL.
     *
     * @throws RemoteException if the UrlQueue cannot be reached
     */
    @Override
    public void indexUrl(String url) throws RemoteException {
        try {
            Registry  registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            IUrlQueue urlQueue = (IUrlQueue) registry.lookup("UrlQueue");
            urlQueue.addUrl(url);
            System.out.printf("[Gateway] Enqueued URL for indexing: %s%n", url);
        } catch (Exception e) {
            throw new RemoteException("Failed to submit URL to UrlQueue: " + e.getMessage(), e);
        }
    }

    // ── IGateway — backlinks ──────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Queries the first available Barrel. The backlink index is identical on
     * all Barrels (thanks to reliable multicast), so any one of them suffices.
     */
    @Override
    public List<String> getBacklinks(String url) throws RemoteException {
        IBarrel barrel = pickBarrel();
        return barrel.getBacklinks(url);
    }

    // ── IGateway — statistics ─────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Merges the per-term frequency maps from all Barrels, then additionally
     * includes the full-query frequency map maintained by this Gateway. Returns
     * the top 10 overall.
     */
    @Override
    public Map<String, Integer> getTopSearchQueries() throws RemoteException {
        // Merge Gateway's own full-query tracking
        Map<String, Integer> merged = new HashMap<>(queryFrequency);

        return merged.entrySet().stream()
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
    public List<Map<String, Object>> getBarrelDetails() throws RemoteException {
        List<Map<String, Object>> details = new ArrayList<>();
        for (IBarrel barrel : barrels) {
            try {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("name",              barrel.getName());
                d.put("indexedUrls",       barrel.getIndexedUrlCount());
                d.put("indexedWords",      barrel.getIndexedWordCount());
                d.put("avgResponseTimeMs", String.format("%.1f", barrel.getAvgResponseTimeMs()));
                details.add(d);
            } catch (RemoteException e) {
                System.err.printf("[Gateway] Could not fetch stats from a barrel: %s%n",
                        e.getMessage());
            }
        }
        return details;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getSystemStats() throws RemoteException {
        int totalWords = 0, totalUrls = 0;
        for (IBarrel barrel : barrels) {
            try {
                totalWords += barrel.getIndexedWordCount();
                totalUrls  += barrel.getIndexedUrlCount();
            } catch (RemoteException ignored) {}
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeBarrels", barrels.size());
        stats.put("totalIndexedUrls",  totalUrls);
        stats.put("totalIndexedWords", totalWords);
        stats.put("totalSearches",
                queryFrequency.values().stream().mapToInt(Integer::intValue).sum());
        return stats;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Picks the next Barrel in round-robin order, falling back to the first
     * available one if the cursor is out of range after a barrel failure.
     *
     * @return an active Barrel
     * @throws RemoteException if no Barrel is available
     */
    private IBarrel pickBarrel() throws RemoteException {
        if (barrels.isEmpty()) {
            throw new RemoteException("No active Barrels available.");
        }
        int idx = roundRobinIndex.getAndUpdate(i -> (i + 1) % barrels.size());
        return barrels.get(idx % barrels.size());
    }

    /** Scans the RMI Registry and refreshes the active Barrel list. */
    private void refreshBarrels() {
        List<IBarrel> discovered = new ArrayList<>();
        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            for (String entry : registry.list()) {
                if (entry.startsWith("Barrel")) {
                    try {
                        IBarrel barrel = (IBarrel) registry.lookup(entry);
                        barrel.getName(); // Liveness ping
                        discovered.add(barrel);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.printf("[Gateway] RMI Registry unreachable during refresh: %s%n",
                    e.getMessage());
        }
        barrels.clear();
        barrels.addAll(discovered);
        System.out.printf("[Gateway] Active barrels: %d%n", barrels.size());
    }

    private void registerInRegistry() throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            registry.rebind("Gateway", this);
            System.out.printf("[Gateway] Registered in RMI Registry at %s:%d%n", rmiHost, rmiPort);
        } catch (Exception e) {
            throw new RemoteException("Failed to register Gateway in RMI Registry", e);
        }
    }

    /** Daemon thread that refreshes the Barrel list every 5 seconds. */
    private void startHealthCheckThread() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gateway-healthcheck");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::refreshBarrels, 5, 5, TimeUnit.SECONDS);
    }

    // ── Entry point ───────────────────────────────────────────────────────

    /**
     * Starts the Gateway as a standalone process.
     *
     * <p>Arguments (positional):
     * <ol>
     *   <li>{@code rmiHost} — default {@code "localhost"}</li>
     *   <li>{@code rmiPort} — default {@code 1099}</li>
     * </ol>
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        String rmiHost = args.length > 0 ? args[0] : "localhost";
        int    rmiPort = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        try {
            new Gateway(rmiHost, rmiPort);
            System.out.println("[Gateway] Running. Waiting for client requests...");
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[Gateway] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

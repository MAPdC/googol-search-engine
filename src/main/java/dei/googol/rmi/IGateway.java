package dei.googol.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote interface exposed by the RMI Gateway.
 *
 * <p>The Gateway is the single entry-point for all external clients (the web
 * application, the RMI console client, etc.). It hides the topology of the
 * Barrel cluster and provides load balancing, fault tolerance, and aggregation.
 *
 * <p>Clients must never talk directly to a Barrel.
 */
public interface IGateway extends Remote {

    // ── Search ───────────────────────────────────────────────────────────

    /**
     * Searches the distributed index for pages that contain <em>all</em> of
     * the terms in {@code query} and returns them ranked by inbound-link count.
     *
     * <p>The Gateway fans out the request to all active Barrels, merges the
     * results, and applies backlink-based ranking before returning.
     *
     * @param query one or more whitespace-separated search terms
     * @return ordered list of result maps; each map contains
     *         {@code "url"}, {@code "title"}, and {@code "snippet"} keys
     * @throws RemoteException on RMI communication failure or no Barrels available
     */
    List<Map<String, String>> search(String query) throws RemoteException;

    // ── URL Indexing ─────────────────────────────────────────────────────

    /**
     * Submits a URL to the URL Queue for future crawling.
     *
     * @param url the URL to schedule for indexing
     * @throws RemoteException on RMI communication failure
     */
    void indexUrl(String url) throws RemoteException;

    // ── Backlinks ────────────────────────────────────────────────────────

    /**
     * Returns all known URLs that link to the given {@code url}.
     *
     * @param url the target page
     * @return list of pages that link to {@code url}; empty if none
     * @throws RemoteException on RMI communication failure
     */
    List<String> getBacklinks(String url) throws RemoteException;

    // ── Statistics ───────────────────────────────────────────────────────

    /**
     * Returns the top-10 most frequently submitted full search queries
     * (not individual words) since the Gateway started.
     *
     * @return ordered map of query → count (descending)
     * @throws RemoteException on RMI communication failure
     */
    Map<String, Integer> getTopSearchQueries() throws RemoteException;

    /**
     * Returns one detail record per active Barrel containing:
     * <ul>
     *   <li>{@code "name"} – barrel identifier</li>
     *   <li>{@code "indexedUrls"} – number of distinct URLs indexed</li>
     *   <li>{@code "indexedWords"} – number of distinct words indexed</li>
     *   <li>{@code "avgResponseTimeMs"} – average search response time (ms)</li>
     * </ul>
     *
     * @return list of barrel detail maps
     * @throws RemoteException on RMI communication failure
     */
    List<Map<String, Object>> getBarrelDetails() throws RemoteException;

    /**
     * Returns aggregate system statistics (total words, total URLs, total
     * searches, active barrels).
     *
     * @return aggregate statistics map
     * @throws RemoteException on RMI communication failure
     */
    Map<String, Object> getSystemStats() throws RemoteException;
}

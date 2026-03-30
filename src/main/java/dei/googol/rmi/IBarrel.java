package dei.googol.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote interface exposed by every Index Storage Barrel.
 *
 * <p>Barrels are the central data store of the Googol system. Multiple Barrel
 * replicas run simultaneously; they all receive the same index updates via a
 * reliable-multicast protocol implemented by the {@link dei.googol.core.Downloader}.
 * The {@link dei.googol.core.Gateway} queries whichever Barrel is available to
 * serve search requests, providing fault tolerance.
 *
 * <p>All methods declare {@link RemoteException} as required by the Java RMI
 * specification.
 */
public interface IBarrel extends Remote {

    // ── Identity ─────────────────────────────────────────────────────────

    /**
     * Returns the unique name this Barrel registered under in the RMI Registry
     * (e.g., {@code "Barrel1"}).
     *
     * @return barrel name
     * @throws RemoteException on RMI communication failure
     */
    String getName() throws RemoteException;

    // ── Index writes ─────────────────────────────────────────────────────

    /**
     * Processes a single index-update message received via reliable multicast.
     * Stop words and words shorter than three characters are silently ignored.
     *
     * @param message the update to apply
     * @throws RemoteException on RMI communication failure
     */
    void processMessage(IndexUpdateMessage message) throws RemoteException;

    /**
     * Records that {@code fromUrl} links to {@code toUrl}.
     * Used by the Downloader to populate the backlink index so that pages can
     * later be ranked by the number of inbound links.
     *
     * @param fromUrl the page that contains the hyperlink
     * @param toUrl   the destination of the hyperlink
     * @throws RemoteException on RMI communication failure
     */
    void addBacklink(String fromUrl, String toUrl) throws RemoteException;

    // ── Index reads ──────────────────────────────────────────────────────

    /**
     * Returns all URLs that contain {@code word}, sorted by descending
     * backlink count (most-linked pages first).
     *
     * <p>Also increments the internal search counter and tracks response time.
     *
     * @param word the term to look up (case-insensitive)
     * @return ordered list of matching URLs; empty list if none found
     * @throws RemoteException on RMI communication failure
     */
    List<String> getUrlsForWord(String word) throws RemoteException;

    /**
     * Returns all known URLs that link to {@code url}.
     *
     * @param url the target page
     * @return list of pages that link to {@code url}; empty if none
     * @throws RemoteException on RMI communication failure
     */
    List<String> getBacklinks(String url) throws RemoteException;

    /**
     * Returns the {@code <title>} of the indexed page, or a placeholder if unknown.
     *
     * @param url the page URL
     * @return page title
     * @throws RemoteException on RMI communication failure
     */
    String getPageTitle(String url) throws RemoteException;

    /**
     * Returns a short text excerpt (≤ 50 words) from the indexed page.
     *
     * @param url the page URL
     * @return text snippet
     * @throws RemoteException on RMI communication failure
     */
    String getPageSnippet(String url) throws RemoteException;

    // ── Statistics ───────────────────────────────────────────────────────

    /**
     * Returns the number of distinct words in this barrel's inverted index.
     *
     * @return indexed word count
     * @throws RemoteException on RMI communication failure
     */
    int getIndexedWordCount() throws RemoteException;

    /**
     * Returns the number of distinct URLs in this barrel's inverted index.
     *
     * @return indexed URL count
     * @throws RemoteException on RMI communication failure
     */
    int getIndexedUrlCount() throws RemoteException;

    /**
     * Returns the average search response time for this barrel in milliseconds.
     *
     * @return average response time (ms), or {@code 0.0} if no searches yet
     * @throws RemoteException on RMI communication failure
     */
    double getAvgResponseTimeMs() throws RemoteException;

    /**
     * Returns the top-10 most searched terms recorded by this barrel, sorted by
     * frequency in descending order.
     *
     * @return ordered map of term → search count
     * @throws RemoteException on RMI communication failure
     */
    Map<String, Integer> getTopSearchTerms() throws RemoteException;

    /**
     * Returns a summary statistics map for this barrel.
     * Keys: {@code "indexedWords"}, {@code "indexedUrls"},
     *       {@code "searchCount"}, {@code "avgResponseTimeMs"}.
     *
     * @return statistics map
     * @throws RemoteException on RMI communication failure
     */
    Map<String, Object> getStatistics() throws RemoteException;
}

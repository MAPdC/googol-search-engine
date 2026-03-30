package dei.googol.rmi;

import java.io.Serial;
import java.io.Serializable;

/**
 * Immutable message object broadcast from a {@link dei.googol.core.Downloader} to every
 * active {@link dei.googol.core.Barrel} via RMI reliable multicast.
 *
 * <p>Each instance carries a single (word → URL) mapping together with the page
 * title and a short text snippet so that the Barrel can index all the metadata in
 * one call.
 *
 * <p>Must be {@link Serializable} because Java RMI serialises method arguments
 * when they cross JVM boundaries.
 */
public final class IndexUpdateMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String word;
    private final String url;
    private final String title;
    private final String snippet;

    /**
     * Constructs a new index-update message.
     *
     * @param word    the indexed term (lower-cased, stop-word-filtered by the caller)
     * @param url     the canonical URL of the page that contains {@code word}
     * @param title   the {@code <title>} of the page
     * @param snippet a short excerpt from the page body (≤ 50 words)
     */
    public IndexUpdateMessage(String word, String url, String title, String snippet) {
        this.word    = word;
        this.url     = url;
        this.title   = title;
        this.snippet = snippet;
    }

    /** @return the indexed term */
    public String getWord()    { return word; }

    /** @return the page URL */
    public String getUrl()     { return url; }

    /** @return the page title */
    public String getTitle()   { return title; }

    /** @return short text excerpt */
    public String getSnippet() { return snippet; }

    @Override
    public String toString() {
        return "IndexUpdateMessage{word='" + word + "', url='" + url + "'}";
    }
}

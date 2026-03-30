package dei.googol.core;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe inverted index that forms the core data structure of a
 * {@link Barrel}.
 *
 * <p><strong>Data model:</strong>
 * <pre>
 *   word  → Set&lt;url&gt;            (inverted index proper)
 *   url   → Set&lt;fromUrl&gt;        (backlink index, for PageRank-style ranking)
 *   url   → title                (page metadata)
 *   url   → snippet              (page metadata)
 * </pre>
 *
 * <p><strong>Concurrency:</strong> All maps are {@link ConcurrentHashMap}
 * instances and individual sets are wrapped with
 * {@link Collections#synchronizedSet} to prevent data races when multiple
 * Downloader threads push updates simultaneously.
 *
 * <p><strong>Persistence:</strong> {@link #saveToDisk(String)} and
 * {@link #loadFromDisk(String)} serialise/deserialise the full state so that a
 * Barrel can recover after a crash without replaying the entire crawl history.
 */
public class InvertedIndex implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** word → set of URLs that contain this word */
    private final ConcurrentHashMap<String, Set<String>> index = new ConcurrentHashMap<>();

    /** url → set of URLs that link TO this URL (backlinks) */
    private final ConcurrentHashMap<String, Set<String>> backlinks = new ConcurrentHashMap<>();

    /** url → page title */
    private final ConcurrentHashMap<String, String> pageTitles = new ConcurrentHashMap<>();

    /** url → short text snippet */
    private final ConcurrentHashMap<String, String> pageSnippets = new ConcurrentHashMap<>();

    /** All distinct URLs that have been indexed at least once */
    private final Set<String> uniqueUrls = Collections.synchronizedSet(new HashSet<>());

    // ── Write operations ──────────────────────────────────────────────────

    /**
     * Associates {@code word} with {@code url} and stores the page metadata.
     * If the URL was already indexed for another word its metadata is updated.
     *
     * @param word    indexed term (caller must have already normalised it)
     * @param url     source URL
     * @param title   page title
     * @param snippet short text excerpt
     */
    public void addToIndex(String word, String url, String title, String snippet) {
        index.computeIfAbsent(word, k -> Collections.synchronizedSet(new HashSet<>()))
             .add(url);
        uniqueUrls.add(url);
        pageTitles.put(url, title);
        pageSnippets.put(url, snippet);
    }

    /**
     * Records that {@code fromUrl} contains a hyperlink pointing to {@code toUrl}.
     *
     * @param fromUrl the page that contains the link
     * @param toUrl   the link destination
     */
    public void addBacklink(String fromUrl, String toUrl) {
        backlinks.computeIfAbsent(toUrl, k -> Collections.synchronizedSet(new HashSet<>()))
                 .add(fromUrl);
    }

    // ── Read operations ───────────────────────────────────────────────────

    /**
     * Returns all URLs that contain {@code word}, sorted by descending backlink
     * count so that more-linked (more relevant) pages appear first.
     *
     * @param word the search term (case-insensitive)
     * @return ordered, unmodifiable list of matching URLs
     */
    public List<String> getUrlsForWord(String word) {
        Set<String> urls = index.getOrDefault(word.toLowerCase(), Collections.emptySet());
        synchronized (urls) {
            return urls.stream()
                    .sorted(Comparator.comparingInt(
                            url -> -backlinks.getOrDefault(url, Collections.emptySet()).size()))
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    /**
     * Returns all known URLs that link to {@code url}.
     *
     * @param url the target page
     * @return unmodifiable list of backlink URLs; empty if none
     */
    public List<String> getBacklinks(String url) {
        Set<String> bl = backlinks.getOrDefault(url, Collections.emptySet());
        synchronized (bl) {
            return List.copyOf(bl);
        }
    }

    /**
     * Returns the number of inbound links for {@code url}.
     *
     * @param url the target page
     * @return backlink count
     */
    public int getBacklinkCount(String url) {
        return backlinks.getOrDefault(url, Collections.emptySet()).size();
    }

    /**
     * Returns the stored title for {@code url}, or a placeholder if not found.
     *
     * @param url page URL
     * @return page title
     */
    public String getPageTitle(String url) {
        return pageTitles.getOrDefault(url, "No title available");
    }

    /**
     * Returns the stored snippet for {@code url}, or a placeholder if not found.
     *
     * @param url page URL
     * @return text snippet
     */
    public String getPageSnippet(String url) {
        return pageSnippets.getOrDefault(url, "No snippet available");
    }

    /** @return number of distinct words in the index */
    public int getWordCount() { return index.size(); }

    /** @return number of distinct URLs that have been indexed */
    public int getUrlCount()  { return uniqueUrls.size(); }

    // ── Persistence ───────────────────────────────────────────────────────

    /**
     * Serialises the full index state to {@code filePath}.
     * Creates parent directories automatically if they do not exist.
     *
     * @param filePath path to the snapshot file (e.g., {@code ./barrel-snapshots/Barrel1.ser})
     * @throws IOException if the file cannot be written
     */
    public void saveToDisk(String filePath) throws IOException {
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(this);
        }
    }

    /**
     * Deserialises and returns a previously saved index from {@code filePath}.
     *
     * @param filePath path to the snapshot file
     * @return restored {@code InvertedIndex}
     * @throws IOException            if the file cannot be read
     * @throws ClassNotFoundException if the serialised class is incompatible
     */
    public static InvertedIndex loadFromDisk(String filePath)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            return (InvertedIndex) ois.readObject();
        }
    }
}

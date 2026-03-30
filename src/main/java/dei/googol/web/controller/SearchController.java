package dei.googol.web.controller;

import dei.googol.web.service.AiService;
import dei.googol.web.service.GatewayService;
import dei.googol.web.service.HackerNewsService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.util.*;

/**
 * MVC controller for the Googol web interface.
 *
 * <p>All endpoints delegate to {@link GatewayService} — the web layer never
 * touches a Barrel directly.
 *
 * <h2>Route map</h2>
 * <pre>
 *   GET  /              → home / search form
 *   GET  /search        → paginated search results + AI analysis
 *   GET  /backlinks     → backlinks for a specific URL
 *   GET  /index         → URL submission form
 *   POST /submit        → submit a URL for indexing
 *   GET  /stats         → live statistics page (WebSocket-driven)
 *   POST /hackernews    → fetch and index matching HN stories
 * </pre>
 */
@Controller
public class SearchController {

    private static final int PAGE_SIZE = 10;

    @Autowired private GatewayService    gatewayService;
    @Autowired private HackerNewsService hackerNewsService;
    @Autowired private AiService         aiService;

    // ── Home ──────────────────────────────────────────────────────────────

    /** Renders the main search form. */
    @GetMapping("/")
    public String home() {
        return "search";
    }

    // ── Search ────────────────────────────────────────────────────────────

    /**
     * Executes a search and renders paginated results.
     *
     * <p>Results are fetched from the Gateway, paginated, and enriched with
     * an optional AI-generated contextual analysis. A subsequent HackerNews
     * import button is included so users can trigger story indexing inline.
     *
     * @param query current search terms
     * @param page  zero-based page number (default 0)
     * @param model Thymeleaf model
     */
    @GetMapping("/search")
    public String search(
            @RequestParam("query") String query,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        List<Map<String, String>> allResults = gatewayService.search(query);

        int totalResults = allResults.size();
        int totalPages   = (int) Math.ceil((double) totalResults / PAGE_SIZE);
        int start        = Math.min(page * PAGE_SIZE, totalResults);
        int end          = Math.min(start + PAGE_SIZE, totalResults);

        List<Map<String, String>> pageResults =
                totalResults > 0 ? allResults.subList(start, end) : Collections.emptyList();

        // AI contextual analysis (null if API key not configured)
        String aiAnalysis = aiService.generateAnalysis(query, allResults);

        model.addAttribute("query",       query);
        model.addAttribute("results",     pageResults);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages",  totalPages);
        model.addAttribute("totalResults", totalResults);
        model.addAttribute("aiAnalysis",  aiAnalysis);

        return "results";
    }

    // ── Backlinks ─────────────────────────────────────────────────────────

    /**
     * Shows all pages that link to a specific URL.
     *
     * @param url   the target page URL
     * @param query the originating search query (used for the Back button)
     * @param model Thymeleaf model
     */
    @GetMapping("/backlinks")
    public String backlinks(
            @RequestParam("url") String url,
            @RequestParam(value = "query", defaultValue = "") String query,
            Model model) {

        List<String> backlinks = gatewayService.getBacklinks(url);
        model.addAttribute("url",       url);
        model.addAttribute("backlinks", backlinks);
        model.addAttribute("query",     query);
        return "backlinks";
    }

    // ── URL indexing ──────────────────────────────────────────────────────

    /** Renders the URL submission form. */
    @GetMapping("/index")
    public String indexForm() {
        return "index";
    }

    /**
     * Accepts a URL from the submission form and forwards it to the crawl queue.
     *
     * @param url   the URL to index
     * @param model Thymeleaf model
     */
    @PostMapping("/submit")
    public String submitUrl(@RequestParam("url") String url, Model model) {
        try {
            gatewayService.indexUrl(url);
            model.addAttribute("message", "URL successfully submitted for indexing.");
            model.addAttribute("success", true);
        } catch (RemoteException e) {
            model.addAttribute("message", "Failed to submit URL: " + e.getMessage());
            model.addAttribute("success", false);
        }
        return "index";
    }

    // ── Statistics ────────────────────────────────────────────────────────

    /**
     * Renders the statistics page.
     * The page connects to {@code /ws} via WebSocket and updates in real time
     * without ever refreshing — the initial model just provides placeholder data.
     */
    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("connected",   gatewayService.isConnected());
        model.addAttribute("barrels",     gatewayService.getBarrelDetails());
        model.addAttribute("topQueries",  gatewayService.getTopSearchQueries());
        model.addAttribute("systemStats", gatewayService.getSystemStats());
        return "stats";
    }

    // ── HackerNews integration ────────────────────────────────────────────

    /**
     * Fetches top Hacker News stories that match the given query terms, submits
     * each matching URL to the Googol index, and redirects back to the search
     * results page.
     *
     * @param query search terms used to filter HN stories
     * @param model Thymeleaf model (for the redirect)
     */
    @PostMapping("/hackernews")
    public String importFromHackerNews(
            @RequestParam("query") String query,
            Model model) {

        List<Map<String, String>> stories = hackerNewsService.fetchMatchingStories(query);
        int indexed = 0;

        for (Map<String, String> story : stories) {
            try {
                gatewayService.indexUrl(story.get("url"));
                indexed++;
            } catch (RemoteException ignored) {}
        }

        // Redirect back to search results with a flash-like parameter
        return "redirect:/search?query=" + encodeQuery(query)
                + "&hnImported=" + indexed;
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private static String encodeQuery(String query) {
        try {
            return java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            return query;
        }
    }
}

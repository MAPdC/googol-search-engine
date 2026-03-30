package dei.googol.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Fetches stories from the <a href="https://github.com/HackerNews/API">Hacker News
 * Firebase REST API</a> and filters them by the user's search terms.
 *
 * <h2>Integration flow</h2>
 * <ol>
 *   <li>Fetch the top-story ID list from
 *       {@code /v0/topstories.json} (up to {@code hackernews.max-stories}).</li>
 *   <li>For each ID, retrieve the story object from
 *       {@code /v0/item/{id}.json}.</li>
 *   <li>Return those stories whose title contains at least one of the search
 *       terms (case-insensitive).</li>
 * </ol>
 *
 * <p>The URLs of matching stories are returned so that the web controller can
 * submit them to the Googol index via the Gateway.
 */
@Service
public class HackerNewsService {

    @Value("${hackernews.api.base-url}")
    private String baseUrl;

    @Value("${hackernews.max-stories}")
    private int maxStories;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Finds Hacker News top stories whose titles match any of the given terms.
     *
     * @param searchTerms whitespace-separated terms from the user's query
     * @return list of story maps, each with {@code "url"} and {@code "title"} keys
     */
    public List<Map<String, String>> fetchMatchingStories(String searchTerms) {
        String[] terms = searchTerms.toLowerCase().split("\\s+");
        List<Map<String, String>> matches = new ArrayList<>();

        try {
            // Step 1 — fetch the list of top-story IDs
            int[] ids = restTemplate.getForObject(baseUrl + "/topstories.json", int[].class);
            if (ids == null) return matches;

            int limit = Math.min(ids.length, maxStories);

            // Step 2 — iterate through IDs and check each story
            for (int i = 0; i < limit && matches.size() < 20; i++) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = restTemplate.getForObject(
                            baseUrl + "/item/" + ids[i] + ".json", Map.class);

                    if (item == null) continue;

                    String title = String.valueOf(item.getOrDefault("title", "")).toLowerCase();
                    String url   = String.valueOf(item.getOrDefault("url",   ""));

                    // Include story if any term appears in its title
                    for (String term : terms) {
                        if (title.contains(term) && url.startsWith("http")) {
                            Map<String, String> story = new LinkedHashMap<>();
                            story.put("title", String.valueOf(item.get("title")));
                            story.put("url",   url);
                            matches.add(story);
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Skip individual story on error; don't abort the whole request.
                    System.err.printf("[HackerNewsService] Could not fetch story %d: %s%n",
                            ids[i], e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.printf("[HackerNewsService] API error: %s%n", e.getMessage());
        }

        return matches;
    }
}

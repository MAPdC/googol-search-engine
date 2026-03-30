package dei.googol.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

//import java.net.http.HttpHeaders;
import java.util.*;

/**
 * Generates contextual AI analysis for search results using any
 * OpenAI-compatible chat-completions REST API.
 *
 * <p>Compatible providers include:
 * <ul>
 *   <li><strong>OpenAI</strong> — {@code https://api.openai.com/v1/chat/completions}</li>
 *   <li><strong>Ollama</strong> — {@code http://localhost:11434/v1/chat/completions}</li>
 *   <li><strong>OpenRouter</strong>, <strong>Mistral</strong>, etc.</li>
 * </ul>
 *
 * <p>To disable AI analysis, leave {@code ai.api.key} blank in
 * {@code application.properties}. When disabled,
 * {@link #generateAnalysis} returns {@code null} and the UI hides the section.
 */
@Service
public class AiService {

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    @Value("${ai.max-tokens}")
    private int maxTokens;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Generates a short contextual analysis based on the search query and
     * the snippets of the top results returned by Googol.
     *
     * @param query   the user's search query
     * @param results the first page of Googol search results
     * @return AI-generated analysis text, or {@code null} if the service is
     *         disabled or an error occurs
     */
    public String generateAnalysis(String query, List<Map<String, String>> results) {
        System.out.println("\n--- [DEBUG AI] INÍCIO DA GERAÇÃO ---");
        System.out.println("[DEBUG AI] A chave da API foi carregada? " + (apiKey != null && !apiKey.isBlank()));
        
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[DEBUG AI] Abortado: A chave está vazia.");
            return null; // AI analysis disabled — no API key configured
        }

        try {
            String prompt = buildPrompt(query, results);
            System.out.println("[DEBUG AI] Prompt gerado com sucesso. A fazer o pedido HTTP à OpenAI...");

            // ── Build the request body ────────────────────────────────────
            Map<String, Object> userMessage = Map.of(
                    "role",    "user",
                    "content", prompt
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model",      model);
            body.put("max_tokens", maxTokens);
            body.put("messages",   List.of(userMessage));

            // ── Set headers ───────────────────────────────────────────────
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // ── Call the API ──────────────────────────────────────────────
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    apiUrl, request, Map.class);

            if (response == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.get("choices");

            if (choices == null || choices.isEmpty()) return null;

            @SuppressWarnings("unchecked")
            Map<String, String> message =
                    (Map<String, String>) choices.get(0).get("message");

            System.out.println("[DEBUG AI] Resposta da OpenAI recebida com sucesso!");

            return message != null ? message.get("content") : null;

        } catch (Exception e) {
            System.err.printf("[AiService] API call failed: %s%n", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Constructs the prompt sent to the AI model.
     * Includes the query and the titles + snippets of up to 5 results for context.
     */
    private String buildPrompt(String query, List<Map<String, String>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful search assistant. ")
          .append("Based on the following search query and top results, ")
          .append("provide a concise contextual analysis in 2–3 sentences. ")
          .append("Do not list the results — synthesise the key themes.\n\n");
        sb.append("Search query: ").append(query).append("\n\n");
        sb.append("Top results:\n");

        int limit = Math.min(5, results.size());
        for (int i = 0; i < limit; i++) {
            Map<String, String> r = results.get(i);
            sb.append(i + 1).append(". ").append(r.getOrDefault("title", "")).append("\n");
            sb.append("   ").append(r.getOrDefault("snippet", "")).append("\n");
        }

        return sb.toString();
    }
}

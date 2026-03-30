package dei.googol.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Pushes real-time system statistics to all connected WebSocket clients.
 *
 * <p>Every 3 seconds this service queries the {@link GatewayService} for the
 * latest stats and broadcasts them to the STOMP topic {@code /topic/stats}.
 * Any browser that has subscribed to that topic receives the update instantly
 * without polling.
 *
 * <p>The payload is a plain Java {@link Map} that Jackson serialises to JSON
 * automatically. Browsers receive a structure like:
 * <pre>
 * {
 *   "connected":      true,
 *   "activeBarrels":  2,
 *   "totalIndexedUrls": 1234,
 *   "totalIndexedWords": 56789,
 *   "totalSearches":  42,
 *   "barrels": [
 *     { "name": "Barrel1", "indexedUrls": 617, "indexedWords": 28000,
 *       "avgResponseTimeMs": "1.3" },
 *     ...
 *   ],
 *   "topQueries": { "distributed systems": 7, "java rmi": 5, ... }
 * }
 * </pre>
 */
@Service
public class StatsWebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private GatewayService gatewayService;

    /**
     * Scheduled task — fires every 3 seconds and pushes a stats snapshot to
     * every WebSocket client subscribed to {@code /topic/stats}.
     */
    @Scheduled(fixedDelay = 3000)
    public void pushStats() {
        Map<String, Object> payload = buildPayload();
        messagingTemplate.convertAndSend("/topic/stats", payload);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Map<String, Object> buildPayload() {
        boolean connected = gatewayService.isConnected();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("connected", connected);

        if (connected) {
            Map<String, Object> sys = gatewayService.getSystemStats();
            payload.putAll(sys);
            payload.put("barrels",    gatewayService.getBarrelDetails());
            payload.put("topQueries", gatewayService.getTopSearchQueries());
        }

        return payload;
    }
}

package dei.googol.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP-over-WebSocket support for real-time server-push updates.
 *
 * <h2>How it works</h2>
 * <p>When a browser connects to {@code /ws}, it establishes a persistent
 * WebSocket (or falls back to SockJS long-polling). The server can then push
 * messages to the topic {@code /topic/stats} at any time without the browser
 * needing to poll. The {@link StatsWebSocketService} pushes a stats payload
 * every time the underlying data changes.
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   WebSocket handshake : /ws  (SockJS fallback enabled)
 *   Subscribe topic     : /topic/stats
 *   Outbound prefix     : /topic  (messages routed to subscribed clients)
 * </pre>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Registers the SockJS/WebSocket handshake endpoint that browsers connect to.
     * SockJS is enabled as a transport fallback for environments that block raw
     * WebSocket connections.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Configures a simple in-memory message broker:
     * <ul>
     *   <li>{@code /topic} — prefix for server-to-client broadcast topics</li>
     *   <li>{@code /app}   — prefix for client-to-server messages (unused here)</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}

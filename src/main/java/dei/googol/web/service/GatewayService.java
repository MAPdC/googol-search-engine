package dei.googol.web.service;

import dei.googol.rmi.IGateway;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Spring service that acts as a proxy between the web layer and the
 * {@link dei.googol.core.Gateway} RMI object.
 *
 * <p>All web controllers and WebSocket services go through this class — they
 * never obtain an RMI reference themselves. This keeps RMI concerns in one
 * place and makes the rest of the web layer easy to test.
 *
 * <p>The Gateway reference is obtained lazily via {@link #gateway()} and
 * refreshed automatically if the Gateway restarts. A background thread
 * re-looks-up the Gateway every 10 seconds so that a transient failure is
 * recovered without a server restart.
 */
@Service
public class GatewayService {

    @Value("${rmi.host}")
    private String rmiHost;

    @Value("${rmi.port}")
    private int rmiPort;

    /** Cached RMI stub — may be {@code null} if the Gateway is unreachable. */
    private volatile IGateway gateway;

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        connectToGateway();
        // Reconnect automatically if the Gateway restarts
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gateway-reconnect");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::connectToGateway, 10, 10, TimeUnit.SECONDS);
    }

    private void connectToGateway() {
        try {
            Registry  registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            IGateway  gw       = (IGateway) registry.lookup("Gateway");
            gw.getSystemStats(); // Liveness check
            this.gateway = gw;
            System.out.printf("[GatewayService] Connected to Gateway at %s:%d%n", rmiHost, rmiPort);
        } catch (Exception e) {
            this.gateway = null;
            System.err.printf("[GatewayService] Could not connect to Gateway: %s%n",
                    e.getMessage());
        }
    }

    /** Returns the cached Gateway stub, or {@code null} if unreachable. */
    private IGateway gateway() {
        return gateway;
    }

    // ── Delegated operations ──────────────────────────────────────────────

    /**
     * Searches the distributed index via the Gateway.
     *
     * @param query whitespace-separated search terms
     * @return ordered list of result maps; empty if no results or Gateway is down
     */
    public List<Map<String, String>> search(String query) {
        IGateway gw = gateway();
        if (gw == null) return Collections.emptyList();
        try {
            return gw.search(query);
        } catch (RemoteException e) {
            System.err.println("[GatewayService] search() failed: " + e.getMessage());
            connectToGateway();
            return Collections.emptyList();
        }
    }

    /**
     * Submits a URL to the crawl queue via the Gateway.
     *
     * @param url the URL to index
     * @throws RemoteException if the Gateway is unreachable
     */
    public void indexUrl(String url) throws RemoteException {
        IGateway gw = gateway();
        if (gw == null) throw new RemoteException("Gateway is currently unavailable.");
        gw.indexUrl(url);
    }

    /**
     * Returns all known backlinks for the given URL.
     *
     * @param url the target page
     * @return list of pages that link to {@code url}
     */
    public List<String> getBacklinks(String url) {
        IGateway gw = gateway();
        if (gw == null) return Collections.emptyList();
        try {
            return gw.getBacklinks(url);
        } catch (RemoteException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Returns the top-10 most searched queries recorded by the Gateway.
     *
     * @return ordered map of query → count; empty if Gateway is down
     */
    public Map<String, Integer> getTopSearchQueries() {
        IGateway gw = gateway();
        if (gw == null) return Collections.emptyMap();
        try {
            return gw.getTopSearchQueries();
        } catch (RemoteException e) {
            return Collections.emptyMap();
        }
    }

    /**
     * Returns per-barrel statistics (name, URL count, word count, avg response time).
     *
     * @return list of barrel detail maps; empty if Gateway is down
     */
    public List<Map<String, Object>> getBarrelDetails() {
        IGateway gw = gateway();
        if (gw == null) return Collections.emptyList();
        try {
            return gw.getBarrelDetails();
        } catch (RemoteException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Returns aggregate system statistics.
     *
     * @return stats map; empty if Gateway is down
     */
    public Map<String, Object> getSystemStats() {
        IGateway gw = gateway();
        if (gw == null) return Collections.emptyMap();
        try {
            return gw.getSystemStats();
        } catch (RemoteException e) {
            return Collections.emptyMap();
        }
    }

    /** @return {@code true} if a live Gateway connection is available */
    public boolean isConnected() {
        return gateway != null;
    }
}

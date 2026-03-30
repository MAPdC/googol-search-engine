package dei.googol.core;

import dei.googol.rmi.IUrlQueue;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared URL Queue that coordinates the web-crawling pipeline.
 *
 * <p>Downloaders call {@link #getNextUrl()} to obtain the next URL to crawl, and
 * call {@link #addUrl(String)} to re-enqueue every hyperlink they discover on
 * each page. A {@link BloomFilter} prevents the same URL from being enqueued
 * more than once, avoiding infinite cycles in the web graph.
 *
 * <p>The queue is registered in the RMI Registry under the name {@code "UrlQueue"}
 * so that any component — Downloader, Gateway, or web server — can submit URLs
 * for indexing regardless of which machine it runs on.
 *
 * <p><strong>Thread safety:</strong> {@link ConcurrentLinkedQueue} handles
 * concurrent polls/offers; a {@link ReentrantLock} serialises the Bloom-filter
 * check-then-add to prevent two threads from concurrently inserting the same URL.
 */
public class UrlQueue extends UnicastRemoteObject implements IUrlQueue {

    /** Lock protecting the Bloom-filter check-then-add critical section. */
    private final ReentrantLock bloomLock = new ReentrantLock();

    private final Queue<String> queue       = new ConcurrentLinkedQueue<>();
    private final BloomFilter   visitedUrls;

    // ── Constructor ───────────────────────────────────────────────────────

    /**
     * Creates and registers the URL Queue.
     *
     * @param rmiHost          hostname of the RMI Registry
     * @param rmiPort          port of the RMI Registry
     * @param bloomFilterSize  number of bits in the Bloom filter bit-set
     * @param numHashFunctions number of hash functions used by the Bloom filter
     * @throws RemoteException on RMI registration failure
     */
    public UrlQueue(String rmiHost, int rmiPort,
                    int bloomFilterSize, int numHashFunctions) throws RemoteException {
        super();
        this.visitedUrls = new BloomFilter(bloomFilterSize, numHashFunctions);
        registerInRegistry(rmiHost, rmiPort);
    }

    // ── IUrlQueue ─────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>The Bloom-filter check and the subsequent insert are performed atomically
     * under {@link #bloomLock} to prevent duplicate enqueuing under concurrent
     * access.
     */
    @Override
    public void addUrl(String url) throws RemoteException {
        if (url == null || url.isBlank()) return;
        bloomLock.lock();
        try {
            if (!visitedUrls.mightContain(url)) {
                visitedUrls.add(url);
                queue.offer(url);
            }
        } finally {
            bloomLock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getNextUrl() throws RemoteException {
        return queue.poll();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() throws RemoteException {
        return queue.isEmpty();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void registerInRegistry(String rmiHost, int rmiPort) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            registry.rebind("UrlQueue", this);
            System.out.println("[UrlQueue] Registered in RMI Registry at " + rmiHost + ":" + rmiPort);
        } catch (Exception e) {
            throw new RemoteException("Failed to register UrlQueue in RMI Registry", e);
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────

    /**
     * Starts the URL Queue as a standalone process.
     *
     * <p>Arguments (optional, positional):
     * <ol>
     *   <li>{@code rmiHost} — default {@code "localhost"}</li>
     *   <li>{@code rmiPort} — default {@code 1099}</li>
     * </ol>
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        String rmiHost = args.length > 0 ? args[0] : "localhost";
        int    rmiPort = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        try {
            new UrlQueue(rmiHost, rmiPort, 1_000_000, 5);
            System.out.println("[UrlQueue] Running. Waiting for URLs...");
            // Keep the JVM alive; the RMI thread handles remote calls.
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[UrlQueue] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

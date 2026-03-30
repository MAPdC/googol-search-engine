package dei.googol.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface for the shared URL Queue.
 *
 * <p>The URL Queue is the central coordinator for the web-crawling pipeline.
 * Downloaders consume URLs from this queue, crawl the resulting pages, and then
 * re-enqueue any hyperlinks they discover — creating an iterative/recursive crawl.
 *
 * <p>A Bloom filter is used internally to avoid re-enqueueing URLs that have
 * already been seen, preventing infinite cycles in the web graph.
 */
public interface IUrlQueue extends Remote {

    /**
     * Adds {@code url} to the queue if it has not been seen before.
     * If the Bloom filter indicates the URL was already enqueued or crawled,
     * the call is silently ignored.
     *
     * @param url the URL to schedule for crawling
     * @throws RemoteException on RMI communication failure
     */
    void addUrl(String url) throws RemoteException;

    /**
     * Removes and returns the next URL to crawl, or {@code null} if the queue
     * is currently empty.
     *
     * @return next URL, or {@code null}
     * @throws RemoteException on RMI communication failure
     */
    String getNextUrl() throws RemoteException;

    /**
     * Returns {@code true} if the queue contains no pending URLs.
     *
     * @return {@code true} when empty
     * @throws RemoteException on RMI communication failure
     */
    boolean isEmpty() throws RemoteException;
}

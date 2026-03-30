package dei.googol.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface exposed by each Downloader instance.
 *
 * <p>Downloaders register themselves in the RMI Registry so that the Gateway
 * can query how many are active. The actual crawl loop runs on a background
 * thread inside each Downloader and does not need to be invoked remotely.
 */
public interface IDownloader extends Remote {

    /**
     * Returns the unique name of this downloader (e.g., {@code "Downloader1"}).
     *
     * @return downloader name
     * @throws RemoteException on RMI communication failure
     */
    String getName() throws RemoteException;
}
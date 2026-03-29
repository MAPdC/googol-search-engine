package dei.googol.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IDownloader extends Remote {
    void startDownloading() throws RemoteException;
}
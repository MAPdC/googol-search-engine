package dei.googol.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IUrlQueue extends Remote {
    void addUrl(String url) throws RemoteException;
    String getNextUrl() throws RemoteException;
    boolean isEmpty() throws RemoteException;
}

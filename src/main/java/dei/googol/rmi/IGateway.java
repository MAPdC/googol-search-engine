package dei.googol.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface IGateway extends Remote {
    List<Map<String, String>> search(String query) throws RemoteException;
    Map<String, Object> getStatistics() throws RemoteException;
    List<String> getBacklinks(String url) throws RemoteException;
    void addUrlGateToQueue(String url) throws RemoteException;
    Map<String, Integer> getTopSearchTerms() throws RemoteException;
}

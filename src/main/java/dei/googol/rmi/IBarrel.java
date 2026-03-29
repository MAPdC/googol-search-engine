package dei.googol.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface IBarrel extends Remote {

    void processMessage(IndexUpdateMessage message) throws RemoteException;
    List<String> getUrlsForWord(String word) throws RemoteException;
    Map<String, Integer> getStatistics() throws RemoteException;
    void addToIndex(String word, String url, String title, String snippet) throws RemoteException;
    String getPageTitle(String url) throws RemoteException;
    String getPageSnippet(String url) throws RemoteException;
    void addBacklink(String fromUrl, String toUrl) throws RemoteException;
    List<String> getBacklinks(String url) throws RemoteException;
    String getName() throws RemoteException;
    Map<String, Integer> getTopSearchTerms() throws RemoteException;
    public int getIndexedWordCount() throws RemoteException;
    public int getIndexedUrlCount() throws RemoteException;
}

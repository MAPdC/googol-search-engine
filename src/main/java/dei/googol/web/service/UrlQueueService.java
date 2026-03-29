package dei.googol.web.service;

import dei.googol.rmi.IUrlQueue;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;

@Service
public class UrlQueueService {

    private IUrlQueue urlQueue;

    @PostConstruct
    public void init() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            urlQueue = (IUrlQueue) registry.lookup("UrlQueue");
        } catch (Exception e) {
            System.err.println("Erro ao conectar ao UrlQueue RMI: " + e.getMessage());
        }
    }

    public void submitUrl(String url) throws RemoteException {
        urlQueue.addUrl(url);
    }
}


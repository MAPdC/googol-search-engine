package dei.googol.web.service;

import dei.googol.rmi.IBarrel;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;

import java.util.List;
import java.util.Map;


@Service
public class BarrelService {

    private IBarrel barrel;

    @PostConstruct
    public void init() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            for (String name : registry.list()) {
                if (name.startsWith("Barrel")) {
                    try {
                        barrel = (IBarrel) registry.lookup(name);
                    } catch (Exception e) {
                        System.err.println("Barrel " + name + " inacessível. Removendo da lista.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Erro ao conectar ao Barrel RMI: " + e.getMessage());
        }
    }

    public List<String> search(String word) throws RemoteException {
        return barrel.getUrlsForWord(word);
    }

    public Map<String, Integer> getStatistics() throws RemoteException {
        return barrel.getStatistics();
    }

    public Map<String, Integer> getTopSearchTerms() throws RemoteException {
        return barrel.getTopSearchTerms();
    }

    public String getTitle(String url) throws RemoteException {
        return barrel.getPageTitle(url);
    }

    public String getSnippet(String url) throws RemoteException {
        return barrel.getPageSnippet(url);
    }
}

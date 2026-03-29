package dei.googol.core;

import dei.googol.rmi.IGateway;
import dei.googol.rmi.IBarrel;
import dei.googol.rmi.IUrlQueue;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.*;

public class Gateway extends UnicastRemoteObject implements IGateway {
    private List<IBarrel> barrels;

    public Gateway() throws RemoteException {
        super();
        this.barrels = discoverBarrels();
        registerGateway();
    }

    // ============ REGISTAR GATEWAY NO RMI ==================

    private void registerGateway() throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("192.168.32.204", 1099);

            registry.rebind("Gateway", this);

            System.out.println("Gateway está pronta e registada no RMI Registry.");
        } catch (Exception e) {
            System.err.println("Erro ao registar a Gateway: " + e.getMessage());
        }
    }

    // =============== EXTRAIR INFORMAÇÃO DOS BARRELS ============

    // ===== SABER QUE BARRELS EXISTEM =====

    private List<IBarrel> discoverBarrels() throws RemoteException {
        List<IBarrel> activeBarrels = new ArrayList<>();
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            for (String name : registry.list()) {
                if (name.startsWith("Barrel")) {
                    try {
                        IBarrel barrel = (IBarrel) registry.lookup(name);
                        barrel.getName();
                        activeBarrels.add(barrel);
                    } catch (Exception e) {
                        System.err.println("Barrel " + name + " inacessível. Removendo da lista.");
                    }
                }
            }
        } catch (RemoteException e) {
            System.err.println("Erro ao acessar o RMI Registry: " + e.getMessage());
        }
        return activeBarrels;
    }


    private synchronized void refreshBarrels() throws RemoteException {
        this.barrels = discoverBarrels();
        if (this.barrels.isEmpty()) {
            throw new RemoteException("Nenhum Barrel disponível após atualização.");
        }
    }

    // ==== SELECIONA BARREL ALEATÓRIO E EXTRAI INFORMAÇÃO ====

    @Override
    public List<Map<String, String>> search(String query) throws RemoteException {
        refreshBarrels();
        List<IBarrel> currentBarrels = new ArrayList<>(this.barrels);

        if (currentBarrels.isEmpty()) {
            throw new RemoteException("Nenhum Barrel disponível");
        }

        String[] terms = query.split("\\s+");
        Map<String, Integer> urlBacklinkCounts = new HashMap<>();
        Map<String, String> urlToTitle = new HashMap<>();
        Map<String, String> urlToSnippet = new HashMap<>();

        // Passo 1: Agregar resultados de TODOS os Barrels
        for (IBarrel barrel : currentBarrels) {
            try {
                List<String> barrelResults = barrel.getUrlsForWord(terms[0]);
                for (String url : barrelResults) {
                    List<String> backlinks = barrel.getBacklinks(url);
                    urlBacklinkCounts.put(url, urlBacklinkCounts.getOrDefault(url, 0) + backlinks.size());

                    if (!urlToTitle.containsKey(url)) {
                        urlToTitle.put(url, barrel.getPageTitle(url));
                        urlToSnippet.put(url, barrel.getPageSnippet(url));
                    }
                }
            } catch (RemoteException e) {
                System.err.println("Barrel " + barrel.getName() + " falhou. Tentando próximo...");
                refreshBarrels();
                throw e;
            }
        }

        // Passo 2: Filtrar URLs que contêm TODOS os termos da pesquisa
        Set<String> filteredUrls = new HashSet<>(urlBacklinkCounts.keySet());
        for (int i = 1; i < terms.length && !filteredUrls.isEmpty(); i++) {
            Set<String> termUrls = new HashSet<>();
            for (IBarrel barrel : currentBarrels) {
                try {
                    termUrls.addAll(barrel.getUrlsForWord(terms[i]));
                } catch (RemoteException e) {
                    System.err.println("Barrel " + barrel.getName() + " falhou. Tentando próximo...");
                    refreshBarrels();
                    throw e;
                }
            }
            filteredUrls.retainAll(termUrls);
        }

        // Passo 3: Ordenar os resultados pelo total de backlinks (decrescente)
        List<String> sortedUrls = new ArrayList<>(filteredUrls);
        sortedUrls.sort((url1, url2) -> {
            int backlinks1 = urlBacklinkCounts.getOrDefault(url1, 0);
            int backlinks2 = urlBacklinkCounts.getOrDefault(url2, 0);
            return Integer.compare(backlinks2, backlinks1);
        });

        // Passo 4: Construir a resposta final
        List<Map<String, String>> searchResults = new ArrayList<>();
        for (String url : sortedUrls) {
            Map<String, String> result = new HashMap<>();
            result.put("title", urlToTitle.get(url));
            result.put("url", url);
            result.put("snippet", urlToSnippet.get(url));
            searchResults.add(result);
        }

        return searchResults;
    }

    // =========== ENVIAR URLs PARA A FILA ===============

    public void addUrlGateToQueue(String url) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            IUrlQueue urlqueue = (IUrlQueue) registry.lookup("UrlQueue");
            urlqueue.addUrl(url);
            System.out.println("URL enviado para a fila: " + url);
        } catch (Exception e) {
            System.err.println("Erro em adicionar URL na fila de URLs: " + e.getMessage());
        }
    }

    // ================ ESTATISTICAS =================

    public Map<String, Object> getStatistics() throws RemoteException {
        refreshBarrels();
        Map<String, Object> stats = new HashMap<>();
        stats.put("Barrels Ativos", barrels.size());

        int totalWords = 0, totalUrls = 0, totalSearches = 0;
        long totalResponseTime = 0;

        Map<String, Map<String, Integer>> individualStats = new HashMap<>();

        for (IBarrel barrel : barrels) {
            try {
                Map<String, Integer> barrelStats = barrel.getStatistics();
                totalWords += barrelStats.get("Palavras indexadas");
                totalUrls += barrelStats.get("URLs indexados");
                totalSearches += barrelStats.get("Pesquisas realizadas");
                totalResponseTime += barrelStats.get("Tempo médio de resposta (ns)");

                individualStats.put(barrel.getName(), barrelStats);
            } catch (RemoteException e) {
                System.err.println("Erro ao acessar estatísticas do Barrel " + barrel.getName());
            }
        }

        stats.put("Total de palavras indexadas", totalWords);
        stats.put("Total de URLs indexados", totalUrls);
        stats.put("Total de pesquisas realizadas", totalSearches);
        stats.put("Tempo médio de resposta (ns)", totalResponseTime / barrels.size());

        stats.put("Estatísticas por Barrel", individualStats);

        return stats;
    }

    @Override
    public Map<String, Integer> getTopSearchTerms() throws RemoteException {
        Map<String, Integer> aggregatedTopSearches = new HashMap<>();

        for (IBarrel barrel : barrels) {
            try {
                Map<String, Integer> barrelTopSearches = barrel.getTopSearchTerms();
                barrelTopSearches.forEach((term, count) ->
                        aggregatedTopSearches.merge(term, count, Integer::sum)
                );
            } catch (RemoteException e) {
                System.err.println("Erro ao acessar top searches do Barrel " + barrel.getName());
            }
        }

        return aggregatedTopSearches.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public Map<String, Map<String, Integer>> getBarrelStats() throws RemoteException {
        Map<String, Map<String, Integer>> stats = new HashMap<>();
        for (IBarrel barrel : barrels) {
            try {
                stats.put(barrel.getName(), barrel.getStatistics());
            } catch (RemoteException e) {
                System.err.println("Erro ao acessar estatísticas do Barrel " + barrel.getName());
            }
        }
        return stats;
    }


    @Override
    public List<String> getBacklinks(String url) throws RemoteException {
        if (!barrels.isEmpty()) {
            return barrels.get(0).getBacklinks(url);
        }
        return Collections.emptyList();
    }


    public static void main(String[] args) {
        try {
            new Gateway();
            System.out.println("Gateway está em execução.");
        } catch (RemoteException e) {
            System.err.println("Erro ao iniciar a Gateway: " + e.getMessage());
        }
    }
}

package dei.googol.core;

import dei.googol.rmi.IBarrel;
import dei.googol.rmi.IndexUpdateMessage;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Barrel extends UnicastRemoteObject implements IBarrel {
    private InvertedIndex index;
    private String name; // Nome único para registar no RMI Registry
    private int searchCount; // Contador de pesquisas realizadas
    private Map<String, Integer> searchTermCounts = new HashMap<>(); // Contador de pesquisas por termo
    private long totalResponseTime = 0; // Tempo total de resposta


    public Barrel(String name) throws RemoteException {
        this.index = new InvertedIndex();
        this.name = name; // "Barrel1@Host" (máquina 1) "Barrel2@VM" -> máquina 2
        registerBarrel(); // Regista o Barrel no RMI Registry
    }

    // =========== RETORNA O NOME DO BARREL ===============
    @Override
    public String getName() throws RemoteException {
        return name;
    }

    // ============ REGISTAR BARREL NO RMI ==================
    private void registerBarrel() {
        try {
            // Obtém ou cria o RMI Registry na porta 1099
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            // Regista o Barrel no RMI Registry com o nome fornecido
            registry.rebind(name, this);

            System.out.println(name + " está pronto e registado no RMI Registry.");
        } catch (Exception e) {
            System.err.println("Erro ao registar o Barrel '" + name + "': " + e.getMessage());
        }
    }

    // ============== ADICIONA WORD E URLs RESPETIVOS ============
    @Override
    public void addToIndex(String word, String url, String title, String snippet) throws RemoteException {
        index.addToIndex(word, url, title, snippet);
        System.out.println(index);
    }

    // ==============
    @Override
    public void processMessage(IndexUpdateMessage message) throws RemoteException {
        String word = message.getWord().toLowerCase();
        if (!STOP_WORDS.contains(word) && word.length() > 2) {
            index.addToIndex(word, message.getUrl(), message.getTitle(), message.getSnippet());

            // LOG DE DEPURAÇÃO
            System.out.println("[BARREL " + name + "] Indexado: '" + word + "' → " + message.getUrl());
        }
    }

    // ================= RETORNA OS URLs ASSOCIADOS À WORD =====================
    @Override
    public List<String> getUrlsForWord(String word) throws RemoteException {
        long startTime = System.nanoTime(); // Inicia a medição do tempo

        searchCount++; // Incrementa o contador de pesquisas
        searchTermCounts.put(word, searchTermCounts.getOrDefault(word, 0) + 1); // Atualiza o contador de termos

        List<String> results = index.getUrlsForWord(word);

        long endTime = System.nanoTime(); // Finaliza a medição do tempo
        totalResponseTime += (endTime - startTime); // Acumula o tempo de resposta

        return results;
    }

    // ================ ESTATÍSTICAS ===================
    @Override
    public Map<String, Integer> getStatistics() throws RemoteException {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("Palavras indexadas", index.getWordCount());
        stats.put("URLs indexados", index.getUrlCount());
        stats.put("Pesquisas realizadas", searchCount);
        stats.put("Tempo médio de resposta (ns)", (int) (totalResponseTime / Math.max(1, searchCount)));
        return stats;
    }

    // ================ RETORNA O TÍTULO ===================
    @Override
    public String getPageTitle(String url) throws RemoteException {
        return index.getPageTitle(url);
    }

    // ================ RETORNA UMA CITAÇÃO ===================
    @Override
    public String getPageSnippet(String url) throws RemoteException {
        return index.getPageSnippet(url);
    }

    // ================ TERMOS MAIS PESQUISADOS ===================
    public Map<String, Integer> getTopSearchTerms() {
        return searchTermCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    // =============== ADICIONAR BACKLINK ======================
    @Override
    public void addBacklink(String fromUrl, String toUrl) throws RemoteException {
        index.addBacklink(fromUrl, toUrl);
    }

    // =============== RETORNA BACKLINKS ========================
    @Override
    public List<String> getBacklinks(String url) throws RemoteException {
        return index.getBacklinks(url);
    }


    @Override
    public int getIndexedUrlCount() throws RemoteException {
        return index.getUrlCount();
    }

    @Override
    public int getIndexedWordCount() throws RemoteException {
        return index.getWordCount();
    }


    // ============== INICIA BARRELS =================
    public static void main(String[] args) {
        try {
            new Barrel(args.length > 0 ? args[0] : "Barrel1@Host");
        } catch (RemoteException e) {
            System.err.println("Erro ao iniciar o Barrel: " + e.getMessage());
        }
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "o", "e", "é", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
            "por", "para", "com", "sem", "que", "como", "mais", "mas", "se", "ou", "ao", "pelo",
            "à", "às", "aos", "um", "uma", "uns", "umas", "the", "and", "of", "to", "in",
            "on", "at", "by", "for", "with", "about", "as", "it", "is", "are", "was", "were",
            "this", "that", "these", "those", "be", "have", "has", "had", "i", "you", "he", "she",
            "we", "they", "me", "him", "her", "us", "them", "my", "your", "his", "its", "our",
            "their", "mine", "yours", "hers", "ours", "theirs"
    );
}
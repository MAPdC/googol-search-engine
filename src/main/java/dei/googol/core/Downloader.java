package dei.googol.core;

import dei.googol.rmi.IBarrel;
import dei.googol.rmi.IDownloader;
import dei.googol.rmi.IUrlQueue;
import dei.googol.rmi.IndexUpdateMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.Serializable;


public class Downloader extends UnicastRemoteObject implements IDownloader, Serializable {
    private List<IBarrel> barrels;
    private String name;

    public Downloader(String name) throws RemoteException {
        this.name = name; // "Downloader1@Host" (máquina 1) Downloader2@VM -> máquina 2
        this.barrels = discoverBarrels();
        registerDownloader();
        startHealthCheckThread();
        new Thread(this::startDownloading).start();
    }

    private void startHealthCheckThread() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    refreshBarrels();
                } catch (InterruptedException e) {
                    System.err.println("Thread de health check interrompida.");
                }
            }
        }).start();
    }

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
            throw e;
        }
        return activeBarrels;
    }

    private synchronized void refreshBarrels() {
        try {
            this.barrels = discoverBarrels();
            System.out.println("Barrels ativos atualizados: " + this.barrels.size());
        } catch (RemoteException e) {
            System.err.println("Erro ao atualizar Barrels: " + e.getMessage());
        }
    }

    // ============ REGISTAR DOWNLOADER NO RMI ==================
    private void registerDownloader() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            registry.rebind(name, this);

            System.out.println(name + " está pronto e registado no RMI Registry.");
        } catch (Exception e) {
            System.err.println("Erro ao registar o Downloader '" + name + "': " + e.getMessage());
        }
    }

    // ============= EXECUTAR WEB CRAWLER ==================
    @Override
    public void startDownloading() {
        while (true) {
            try {
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                IUrlQueue urlqueue = (IUrlQueue) registry.lookup("UrlQueue");
                String url = urlqueue.getNextUrl();

                if (url != null) {
                    processUrl(url);
                } else {
                    Thread.sleep(1000);
                    if (barrels.isEmpty()) refreshBarrels();
                }
            } catch (Exception e) {
                System.err.println("Erro geral no Downloader: " + e.getMessage());
            }
        }
    }

    private void processUrl(String url) {
        try {
            Document doc = Jsoup.connect(url).get();
            String title = doc.title();
            String text = doc.text();
            String[] words = text.split("[^a-zA-Z0-9]+");
            String snippet = String.join(" ", Arrays.copyOfRange(words, 0, Math.min(50, words.length)));

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String nextUrl = link.attr("abs:href");
                try {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                    IUrlQueue urlqueue = (IUrlQueue) registry.lookup("UrlQueue");
                    urlqueue.addUrl(nextUrl);

                    for (IBarrel barrel : barrels) {
                        try {
                            barrel.addBacklink(url, nextUrl);
                        } catch (RemoteException e) {
                            System.err.println("Erro ao registrar backlink no Barrel: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao adicionar URL na fila: " + e.getMessage());
                }
            }

            for (String word : words) {
                String cleanWord = word.toLowerCase().replaceAll("[^a-záéíóúãõâêôç]", "");
                if (cleanWord.length() > 2 && !STOP_WORDS.contains(cleanWord)) {
                    IndexUpdateMessage message = new IndexUpdateMessage(cleanWord, url, title, snippet);
                    sendToBarrels(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao processar URL: " + url);
        }
    }

    // ============= ENVIA MESSAGE PARA TODOS OS BARRELS ==============
    private void sendToBarrels(IndexUpdateMessage message) {
        List<IBarrel> barrelsToRemove = new ArrayList<>();

        for (IBarrel barrel : barrels) {
            try {
                barrel.processMessage(message);
                try {
                    System.out.println("Enviado para Barrel: " + barrel.getName());
                } catch (RemoteException e) {
                    System.err.println("Erro ao obter nome do Barrel (mas mensagem foi enviada)");
                }
            } catch (RemoteException e) {
                try {
                    System.err.println("Barrel " + barrel.getName() + " inacessível. Marcado para remoção.");
                } catch (RemoteException ex) {
                    System.err.println("Barrel inacessível (nome não disponível). Marcado para remoção.");
                }
                barrelsToRemove.add(barrel);
            }
        }
    }

    // ============= INICIA O DOWNLOADER =================
    public static void main(String[] args) {
        try {
            Downloader downloader = new Downloader(args[0]);
            downloader.startDownloading();
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o Downloader: " + e.getMessage());
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

package dei.googol.core;

import dei.googol.rmi.IUrlQueue;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UrlQueue extends UnicastRemoteObject implements IUrlQueue {
    private final Queue<String> urlQueue = new ConcurrentLinkedQueue<>();
    private final BloomFilter visitedUrls;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public UrlQueue(int bloomFilterSize, int numHashFunctions) throws RemoteException {
        super();
        this.visitedUrls = new BloomFilter(bloomFilterSize, numHashFunctions);
        registerUrlQueue();
    }

    // ============ REGISTAR URL QUEUE NO RMI ==================

    private void registerUrlQueue() throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            registry.rebind("UrlQueue", this);

            System.out.println("UrlQueue está pronta e registada no RMI Registry.");
        } catch (Exception e) {
            System.err.println("Erro ao registar a UrlQueue: " + e.getMessage());
        }
    }

    // ============ ADICIONA À FILA SE NÃO TIVER SIDO VISITADO ==============
    @Override
    public void addUrl(String url) throws RemoteException {
        lock.writeLock().lock();
        try {
            if (!visitedUrls.mightContain(url)) {
                urlQueue.add(url);
                visitedUrls.add(url);
                System.out.println("URL adicionado: " + url);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ============ RETORNA O PRÓXIMO URL A SER VISITADO ============
    @Override
    public String getNextUrl() throws RemoteException {
        String url = urlQueue.poll();
        if (url != null) {
            lock.writeLock().lock();
            try {
                visitedUrls.add(url);
            } finally {
                lock.writeLock().unlock();
            }
        }
        return url;
    }

    //============ RETORNA SE A FILA ESTÁ VAZIA OU NÃO =============
    public synchronized boolean isEmpty() throws RemoteException {
        return urlQueue.isEmpty();
    }

    public static void main(String[] args) {
        try {
            new UrlQueue(1000000, 3);
            System.out.println("URL Queue está em execução.");
        } catch (RemoteException e) {
            System.err.println("Erro ao iniciar a URL Queue: " + e.getMessage());
            e.printStackTrace();
        }
    }

}

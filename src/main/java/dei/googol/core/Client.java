package dei.googol.core;

import dei.googol.rmi.IGateway;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.util.*;

public class Client {

    private static void displayResults(List<Map<String, String>> results, IGateway gateway) {
        Scanner scanner = new Scanner(System.in);
        int page = 0;
        int pageSize = 10;
        int totalPages = (int) Math.ceil((double) results.size() / pageSize);

        do {
            System.out.println("\n=== Resultados ===");
            System.out.println("Mostrando página " + (page + 1) + " de " + totalPages);
            System.out.println("Total de resultados: " + results.size() + "\n");

            int start = page * pageSize;
            int end = Math.min(start + pageSize, results.size());

            for (int i = start; i < end; i++) {
                Map<String, String> result = results.get(i);
                String url = result.get("url");

                try {
                    List<String> backlinks = gateway.getBacklinks(url);

                    System.out.println("[" + (i+1) + "] " + result.get("title"));
                    System.out.println("URL: " + url);
                    System.out.println("Backlinks (" + backlinks.size() + "):");

                    int maxToShow = Math.min(3, backlinks.size());
                    for (int j = 0; j < maxToShow; j++) {
                        System.out.println("  • " + backlinks.get(j));
                    }

                    if (backlinks.size() > 3) {
                        System.out.println("  • ... e mais " + (backlinks.size() - 3) + " backlinks");
                    } else if (backlinks.isEmpty()) {
                        System.out.println("  • Nenhum backlink encontrado");
                    }

                    System.out.println("Citação: " + result.get("snippet"));
                    System.out.println("-----");
                } catch (RemoteException e) {
                    System.err.println("Erro ao obter backlinks: " + e.getMessage());
                }
            }

            System.out.println("\nOpções:");
            System.out.println("1. Próxima página");
            System.out.println("2. Página anterior");
            System.out.println("3. Ver todos backlinks de um resultado");
            System.out.println("4. Voltar ao menu principal");
            System.out.print("Escolha: ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1:
                    if (page < totalPages - 1) page++;
                    else System.out.println("Está na última página!");
                    break;
                case 2:
                    if (page > 0) page--;
                    else System.out.println("Está na primeira página!");
                    break;
                case 3:
                    System.out.print("Digite o número do resultado: ");
                    int selected = scanner.nextInt() - 1;
                    scanner.nextLine();

                    if (selected >= start && selected < end) {
                        showAllBacklinks(results.get(selected), gateway);
                    } else {
                        System.out.println("Número inválido!");
                    }
                    break;
                case 4:
                    return;
                default:
                    System.out.println("Opção inválida!");
            }
        } while (true);
    }

    private static void showAllBacklinks(Map<String, String> result, IGateway gateway) {
        try {
            System.out.println("\n=== Backlinks completos ===");
            System.out.println("Título: " + result.get("title"));
            System.out.println("URL: " + result.get("url"));

            List<String> backlinks = gateway.getBacklinks(result.get("url"));
            System.out.println("Total: " + backlinks.size() + " backlinks\n");

            if (backlinks.isEmpty()) {
                System.out.println("Nenhum backlink encontrado para esta página.");
            } else {
                for (String backlink : backlinks) {
                    System.out.println("- " + backlink);
                }
            }

            System.out.println("\nPressione Enter para continuar...");
            new Scanner(System.in).nextLine();
        } catch (RemoteException e) {
            System.err.println("Erro ao obter backlinks: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("192.168.32.204", 1099);

            IGateway gateway = (IGateway) registry.lookup("Gateway");

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("1. Pesquisar");
                System.out.println("2. Consultar estatísticas");
                System.out.println("3. Indexar URL manualmente");
                System.out.println("4. Sair");
                System.out.print("Escolha uma opção: ");
                int choice = scanner.nextInt();
                scanner.nextLine();

                if (choice == 1) {
                    String query = scanner.nextLine();

                    try {
                        List<Map<String, String>> results = gateway.search(query);
                        if (results.isEmpty()) {
                            System.out.println("Nenhum resultado encontrado.");
                        } else {
                            displayResults(results, gateway);
                        }
                    } catch (RemoteException e) {
                        System.err.println("Erro na pesquisa: " + e.getMessage());
                    }
                } else if (choice == 2) {
                    try {
                        Map<String, Object> stats = gateway.getStatistics();
                        System.out.println("\n=== Estatísticas do Sistema ===");
                        System.out.println("Barrels Ativos: " + stats.get("Barrels Ativos"));
                        System.out.println("Total de palavras indexadas: " + stats.get("Total de palavras indexadas"));
                        System.out.println("Total de URLs indexados: " + stats.get("Total de URLs indexados"));
                        System.out.println("Total de pesquisas realizadas: " + stats.get("Total de pesquisas realizadas"));
                        System.out.println("Tempo médio de resposta (ns): " + stats.get("Tempo médio de resposta (ns)"));

                        @SuppressWarnings("unchecked")
                        Map<String, Map<String, Integer>> individualStats = (Map<String, Map<String, Integer>>) stats.get("Estatísticas por Barrel");

                        System.out.println("\n=== Estatísticas por Barrel ===");
                        for (Map.Entry<String, Map<String, Integer>> entry : individualStats.entrySet()) {
                            System.out.println("\nBarrel: " + entry.getKey());
                            Map<String, Integer> barrelStats = entry.getValue();
                            System.out.println("  Palavras indexadas: " + barrelStats.get("Palavras indexadas"));
                            System.out.println("  URLs indexados: " + barrelStats.get("URLs indexados"));
                            System.out.println("  Pesquisas realizadas: " + barrelStats.get("Pesquisas realizadas"));
                            System.out.println("  Tempo médio de resposta (ns): " + barrelStats.get("Tempo médio de resposta (ns)"));
                        }

                        System.out.println("\n=== Top 10 Pesquisas ===");
                        Map<String, Integer> topSearches = gateway.getTopSearchTerms();
                        if (topSearches.isEmpty()) {
                            System.out.println("Nenhuma pesquisa registrada ainda.");
                        } else {
                            topSearches.forEach((term, count) ->
                                    System.out.println("- " + term + ": " + count + " pesquisas")
                            );
                        }

                    } catch (RemoteException e) {
                        System.err.println("Erro ao consultar estatísticas: " + e.getMessage());
                    }
                } else if (choice == 3) {
                    System.out.println("Introduza o URL a indexar: ");
                    String url = scanner.nextLine();

                    gateway.addUrlGateToQueue(url);
                    System.out.println("URL adicionado à fila para indexação.");
                } else if (choice == 4) {
                    break;
                } else {
                    System.out.println("Opção inválida.");
                }
            }
            scanner.close();
        } catch (Exception e) {
            System.err.println("Erro no cliente: " + e.getMessage());
        }
    }
}
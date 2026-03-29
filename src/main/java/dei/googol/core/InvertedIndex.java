package dei.googol.core;

import java.util.*;
import java.util.stream.Collectors;

public class InvertedIndex {
    private HashMap<String, HashSet<String>> index = new HashMap<>();
    private HashMap<String, Integer> linkCounts = new HashMap<>();
    private HashMap<String, HashSet<String>> backlinks = new HashMap<>(); // Backlinks
    private HashMap<String, Integer> backlinkCounts = new HashMap<>(); // Contagem de backlinks
    private HashSet<String> uniqueUrls = new HashSet<>(); // URLs únicos indexados
    private HashMap<String, String> pageTitles = new HashMap<>(); // Título da página
    private HashMap<String, String> pageSnippets = new HashMap<>(); // Citação curta

    public int getWordCount() {
        return index.size();
    }

    public int getUrlCount() {
        return uniqueUrls.size();
    }

    public List<String> getBacklinks(String url) {
        return new ArrayList<>(backlinks.getOrDefault(url, new HashSet<>()));
    }

    public String getPageTitle(String url) {
        return pageTitles.getOrDefault(url, "Sem título");
    }

    public String getPageSnippet(String url) {
        return pageSnippets.getOrDefault(url, "Sem citação");
    }

    public void addToIndex(String word, String url, String title, String snippet) {
        if (!index.containsKey(word)) {
            index.put(word, new HashSet<>());
        }
        index.get(word).add(url);
        uniqueUrls.add(url);
        pageTitles.put(url, title);
        pageSnippets.put(url, snippet);
    }

    public void addBacklink(String fromUrl, String toUrl) {
        if(!backlinks.containsKey(toUrl)) {
            backlinks.put(toUrl, new HashSet<>());
        }
        backlinks.get(toUrl).add(fromUrl);
        backlinkCounts.put(toUrl, backlinkCounts.getOrDefault(toUrl, 0) + 1);
    }

    public List<String> getUrlsForWord(String word) {
        String lowerCaseWord = word.toLowerCase();
        HashSet<String> urls = index.getOrDefault(lowerCaseWord, new HashSet<>());
        List<String> urlList = new ArrayList<>(urls);

        return urlList.stream()
                .sorted((url1, url2) -> backlinkCounts.getOrDefault(url2, 0) - backlinkCounts.getOrDefault(url1, 0))
                .collect(Collectors.toList());
    }

    public void incrementLinkCount (String url) {
        linkCounts.put(url, linkCounts.getOrDefault(url, 0) + 1);
    }

    public void printIndex() {
        for (String word : index.keySet()) {
            System.out.println(word + ":" + index.get(word));
        }
    }

}

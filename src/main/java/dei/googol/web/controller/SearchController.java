package dei.googol.web.controller;

import dei.googol.web.service.BarrelService;
import dei.googol.web.service.UrlQueueService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.rmi.RemoteException;
import java.util.*;



@Controller
public class SearchController {

    @Autowired
    private BarrelService barrelService;

    @Autowired
    private UrlQueueService urlQueueService;

    @GetMapping("/")
    public String index() {
        return "search";
    }

    @GetMapping("/search")
    public String search(
            @RequestParam String word,
            @RequestParam(defaultValue = "1") int page,
            Model model) throws RemoteException {

        List<String> allResults = barrelService.search(word);

        int pageSize = 10;
        int totalResults = allResults.size();

        int start = page * pageSize;
        int end = Math.min(start + pageSize, totalResults);

        List<String> pageResults = new ArrayList<>();
        if (start <= end && start < totalResults) {
            pageResults = allResults.subList(start, end);
        }

        Map<String, String> titles = new HashMap<>();
        Map<String, String> snippets = new HashMap<>();
        for (String url : pageResults) {
            titles.put(url, barrelService.getTitle(url));
            snippets.put(url, barrelService.getSnippet(url));
        }

        int totalPages = (totalResults + pageSize - 1) / pageSize;

        model.addAttribute("word", word);
        model.addAttribute("results", pageResults);
        model.addAttribute("titles", titles);
        model.addAttribute("snippets", snippets);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);

        return "results";
    }

    @GetMapping("/stats")
    public String stats(Model model) throws RemoteException {
        model.addAttribute("stats", barrelService.getStatistics());
        model.addAttribute("topTerms", barrelService.getTopSearchTerms());
        return "stats";
    }

    @GetMapping("/index")
    public String indexForm() {
        return "index";
    }

    @GetMapping("/submit")
    public String submitUrl(@RequestParam("url") String url, Model model) {
        try {
            urlQueueService.submitUrl(url);
            model.addAttribute("message", "URL enviado com sucesso!");
        } catch (Exception e) {
            model.addAttribute("message", "Erro ao enviar URL: " + e.getMessage());
        }
        return "index";
    }

}
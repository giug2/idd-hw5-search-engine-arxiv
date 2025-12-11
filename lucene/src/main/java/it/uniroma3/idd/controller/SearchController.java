package it.uniroma3.idd.controller;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.dto.SearchResult;
import it.uniroma3.idd.service.Searcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


@Controller
public class SearchController {
    
    private final Searcher searcher;
    private final LuceneConfig luceneConfig;

    @Autowired
    public SearchController(Searcher searcher, LuceneConfig luceneConfig) {
        this.searcher = searcher;
        this.luceneConfig = luceneConfig;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/")
    public String search(@RequestParam("query") String query, 
                         @RequestParam(value = "type", defaultValue = "article") String type,
                         Model model) {
        if (query == null || query.trim().isEmpty()) {
            model.addAttribute("error", "Inserisci una query valida.");
            return "index";
        }

        try {
            String[] parts = query.trim().split("\\s+", 2);
            if (parts.length < 2) {
                model.addAttribute("error", "Sintassi: <campo> <termine_query>");
                model.addAttribute("info", "Esempio: title \"cancer therapy\" oppure body \"protein structure\"");
                return "index";
            }

            String field = parts[0].toLowerCase();
            String queryText = parts[1];

            // Validate field based on type
            boolean isValid = false;
            if ("article".equalsIgnoreCase(type)) {
                switch (field) {
                    case "title":
                    case "authors":
                    case "paragraphs":
                    case "articleabstract":
                    case "publicationdate":
                        isValid = true;
                        break;
                }
            } else if ("table".equalsIgnoreCase(type) || "figure".equalsIgnoreCase(type)) {
                switch (field) {
                    case "caption":
                    case "body": 
                    case "informative_terms":
                    case "citing_paragraphs":
                    case "contextual_paragraphs":
                        isValid = true;
                        break;
                }
            }

            if (!isValid) {
                model.addAttribute("error", "Campo '" + field + "' non valido per il tipo '" + type + "'.");
                return "index";
            }
            
            if (field.equals("articleabstract")) field = "articleAbstract";
            if (field.equals("publicationdate")) field = "publicationDate";

            List<SearchResult> results = searcher.search(field, queryText);

            model.addAttribute("results", results);
            model.addAttribute("query", query);

        } catch (Exception e) {
            model.addAttribute("error", "Errore: " + e.getMessage());
            e.printStackTrace();
        }

        return "index";
    }

    @GetMapping("/file/{fileName:.+}")
    @ResponseBody
    public Resource serveFile(@PathVariable String fileName) {
        Path file = Paths.get(luceneConfig.getArticlesPath()).resolve(fileName).normalize();
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) {
            throw new RuntimeException("File non trovato: " + fileName);
        }
        return resource;
    }
}

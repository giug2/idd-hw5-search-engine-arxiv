package it.uniroma3.idd.controller;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.dto.SearchResult;
import it.uniroma3.idd.service.Searcher;


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
    public String search(@RequestParam("query") String query, Model model) {
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

            String field = parts[0].toLowerCase();          //campo di ricerca (title, authirs, ecc)
            String queryText = parts[1];                    //parola cercata

            // Validate field against known fields in LuceneIndexer
            switch (field) {
                case "title":
                case "authors":
                case "paragraphs":
                case "articleabstract":
                case "publicationdate":
                    break;
                default:
                    model.addAttribute("error", "Campo non valido. Campi supportati: title, authors, paragraphs, articleAbstract, publicationDate");
                    return "index";
            }
            
            // Handle camelCase for articleAbstract and publicationDate
            if (field.equals("articleabstract")) {
                field = "articleAbstract";
            } else if (field.equals("publicationdate")) {
                field = "publicationDate";
            }

            List<SearchResult> results = searcher.search(field, queryText);

            model.addAttribute("results", results);
            model.addAttribute("query", query);

        } catch (Exception e) {
            model.addAttribute("error", "Errore: " + e.getMessage());
            e.printStackTrace();
        }

        return "index";
    }

    @GetMapping("/view/{fileName:.+}")
    public String viewArticle(@PathVariable String fileName, Model model) {
        try {
            Path filePath = Paths.get(luceneConfig.getArticlesPath()).resolve(fileName).normalize();
            File file = filePath.toFile();
            
            if (!file.exists()) {
                model.addAttribute("error", "File non trovato: " + fileName);
                return "index";
            }

            Document document = Jsoup.parse(file, "UTF-8");
            
            // Extract fields using the same logic as Parser.java
            String title = document.select("article-title").first() != null ? document.select("article-title").first().text() : "No Title Found";
            
            List<String> authors = new ArrayList<>();
            document.select("contrib[contrib-type=author] name").forEach(nameElement -> {
                String surname = nameElement.select("surname").text();
                String givenNames = nameElement.select("given-names").text();
                authors.add(givenNames + " " + surname);
            });
            
            String articleAbstract = document.select("abstract p").first() != null ? document.select("abstract p").text() : "No Abstract Found";
            
            // Date extraction
            String publicationDate = "Unknown Date";
            org.jsoup.nodes.Element pubDateElement = document.select("pub-date").first();
            if (pubDateElement != null) {
                String year = pubDateElement.select("year").text();
                String month = pubDateElement.select("month").text();
                String day = pubDateElement.select("day").text();
                
                if (!year.isEmpty()) {
                    publicationDate = year;
                    if (!month.isEmpty()) {
                        publicationDate += "-" + (month.length() == 1 ? "0" + month : month);
                        if (!day.isEmpty()) {
                            publicationDate += "-" + (day.length() == 1 ? "0" + day : day);
                        }
                    }
                }
            }

            List<String> paragraphs = new ArrayList<>();
            document.select("body p").forEach(paragraph -> paragraphs.add(paragraph.text()));

            model.addAttribute("fileName", fileName);
            model.addAttribute("title", title);
            model.addAttribute("authors", authors);
            model.addAttribute("articleAbstract", articleAbstract);
            model.addAttribute("publicationDate", publicationDate);
            model.addAttribute("paragraphs", paragraphs);
            
            return "article";

        } catch (Exception e) {
            model.addAttribute("error", "Errore durante la lettura del file: " + e.getMessage());
            return "index";
        }
    }

    @GetMapping("/file/{fileName:.+}")
    @ResponseBody
    public org.springframework.core.io.Resource serveFile(@PathVariable String fileName) {
        try {
            Path file = Paths.get(luceneConfig.getArticlesPath()).resolve(fileName).normalize();
            if (file == null) {
                throw new RuntimeException("Percorso file non valido: " + fileName);
            }
            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(file);
            if (!resource.exists()) {
                throw new RuntimeException("File non trovato: " + fileName);
            }
            return resource;
        } catch (Exception e) {
            throw new RuntimeException("Errore nel recupero del file: " + fileName, e);
        }
    }
}

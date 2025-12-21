package it.uniroma3.idd.controller;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.apache.lucene.queryparser.classic.ParseException;
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
    public String search(
        @RequestParam("query") String query, 
        @RequestParam(name = "indices", required = false) List<String> indicesScelti,
        Model model) {

        /*VALIDAZIONE INPUT UTENTE*/
        if (query == null || query.trim().isEmpty()) {
            model.addAttribute("error", "Inserisci una query valida.");
            return "index";
        }

        // Deve essere selezionato almeno un indice
        if (indicesScelti == null || indicesScelti.isEmpty()) {
             model.addAttribute("error", "Seleziona almeno un indice di ricerca (Articoli, Tabelle, etc.).");
             // Passa la query corrente per non perderla
             model.addAttribute("query", query); 
             return "index";
        }

        // Nella modalità "input libero", passiamo SEMPRE NULL come campo specifico.
        // Il Searcher interpreterà la stringa 'query' interamente, usando QueryParser 
        // per la ricerca singola (se la query contiene 'campo:parola') o MultiFieldQueryParser 
        // per la ricerca combinata (se la query contiene solo 'parola').
        String campo = null;                                                                                                                                //TODO gestione campo?


        try {
            Map<String, List<SearchResult>> risultati = searcher.search(query.trim(), indicesScelti, campo);
            model.addAttribute("risultatiTotali", risultati); 
            model.addAttribute("query", query);
            model.addAttribute("indicesScelti", indicesScelti);
        } catch (ParseException e) {
            model.addAttribute("error", "Errore di sintassi nella query Lucene. Controlla il formato (es. title:term AND term): " + e.getMessage());
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Errore di configurazione dell'indice: " + e.getMessage());
        } catch (Exception e) {
            model.addAttribute("error", "Si è verificato un errore inatteso: " + e.getMessage());
            e.printStackTrace();
        }
        return "index";
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

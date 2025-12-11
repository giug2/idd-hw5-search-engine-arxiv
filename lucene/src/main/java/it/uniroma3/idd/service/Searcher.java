package it.uniroma3.idd.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.dto.SearchResult;

@Service
public class Searcher {
    private final Path indexPath;
    private final Analyzer analyzer;

    private final Map<String, IndexSearcher> searcherMap = new HashMap<>();

    @Autowired
    public Searcher(LuceneConfig luceneConfig, Analyzer perFieldAnalyzer) {
        this.indexPath = Paths.get(luceneConfig.getIndexDirectory());
        this.analyzer = perFieldAnalyzer;
    }


    //!===============================================================================
    //!                         FUNZIONE DI RICERCA PRINCIPALE
    //!===============================================================================
    public List<SearchResult> search(String queryText, List<String> indicesScelti, String campoScelto) throws Exception {

        //field: campo di ricerca title, ecc
        //querytext: termine cercato

        for (String currentIndex : indicesScelti) {
            IndexSearcher currentSearcher = searcherMap.get(currentIndex);

            if (currentSearcher == null) {
                System.err.println("Indice non trovato o non caricato: " + currentIndex);
                continue; 
            }

            Query query = buildQuery(queryText, indicesScelti, campoScelto);
            TopDocs hits = currentSearcher.search(query, 10);

            //TODO

        }


        List<SearchResult> resultsList = new ArrayList<>();

            //todo 
            //Query query;

            // Esegui la ricerca (top 10 risultati)
            TopDocs results = searcher.search(query, 10);
            ScoreDoc[] hits = results.scoreDocs;

            for (ScoreDoc hit : hits) {
                Document doc = searcher.storedFields().document(hit.doc);
                // Use "id" as the fileName, as it stores the filename
                String fileName = doc.get("id");
                if (fileName == null) {
                    fileName = "Unknown ID";
                }
                
                String title = doc.get("title");
                if (title == null) {
                    title = "No Title";
                }
                
                String publicationDate = doc.get("publicationDate");
                if (publicationDate == null) {
                    publicationDate = "N/A";
                }
                
                resultsList.add(new SearchResult(fileName, title, publicationDate, hit.score));
            }

        return resultsList;
    }


    //!metodo di supporto che costruisce una query specifica per il caso di utilizzo
    private Query buildQuery(String testoRicerca, List<String> indexKey, String campoScelto) throws ParseException {

        //testoRicerca: effettiva ricerca dell'utente
        //indexKey:target della ricerca (file, tabella, immagine)
        //campoScelto: di default a null

        // Se campoScelto NON è nullo, l'utente vuole usare la sintassi completa (es. "title:term")
        if (campoScelto != null && !campoScelto.isEmpty()) {
            
            // Usiamo la sintassi Lucene "campo:query"
            String queryInSintassiLucene = campoScelto + ":" + testoRicerca;

            // Usiamo un QueryParser generico per interpretare la sintassi Lucene completa.
            QueryParser parser = new QueryParser("id", analyzer); 
            
            return parser.parse(queryInSintassiLucene); 
        }
        
        // Logica per Ricerca Combinata/Generica (MultiFieldQuery)
        String[] defaultFields;
        switch (indexKey.toLowerCase()) {
            case "articoli":
                defaultFields = new String[]{"title", "authors", "articleAbstract", "paragraphs"};
                break;
            case "tabelle":
                defaultFields = new String[]{"caption", "body", "mentions", "terms", "context_paragraphs"};
                break;
            default:
                defaultFields = new String[]{}; 
                break;
        }
        
        if (defaultFields.length == 0) {
            throw new ParseException("Nessun campo di ricerca predefinito trovato per l'indice: " + indexKey);
        }

        // L'utilizzo dell'istanza risolve l'errore di tipizzazione
        MultiFieldQueryParser multiParser = new MultiFieldQueryParser(defaultFields, analyzer);
        return multiParser.parse(testoRicerca);
    }
















    public List<Document> searchDocuments(String field, String queryText) throws Exception {
        List<Document> resultsList = new ArrayList<>();

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(indexPath))) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Query query;
            QueryParser parser;
            
            parser = new QueryParser(field, analyzer);
            query = parser.parse(queryText);

            // Esegui la ricerca (top 10 risultati)
            TopDocs results = searcher.search(query, 10);
            ScoreDoc[] hits = results.scoreDocs;

            for (ScoreDoc hit : hits) {
                Document doc = searcher.storedFields().document(hit.doc);
                resultsList.add(doc);
            }
        }

        return resultsList;
    }
}

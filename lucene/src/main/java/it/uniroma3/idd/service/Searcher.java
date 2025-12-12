package it.uniroma3.idd.service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.dto.SearchResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class Searcher {
    private final Path indexPath;
    private final Analyzer analyzer;

    private final Map<String, IndexSearcher> searcherMap = new HashMap<>();
    private final Map<String, DirectoryReader> readerMap = new HashMap<>();

    @Value("#{${lucene.indices.map}}")
    private Map<String, String> indexPaths; 

    @Autowired
    public Searcher(LuceneConfig luceneConfig, Analyzer perFieldAnalyzer) {
        this.indexPath = Paths.get(luceneConfig.getIndexDirectory());
        this.analyzer = perFieldAnalyzer;
    }

    @PostConstruct
    public void init()throws IOException {
        System.out.println("Inizializzazione searcher");

        for (Map.Entry<String, String> entry : indexPaths.entrySet()) {
            String indexKey = entry.getKey();
            String path = entry.getValue();
            
            try {
                DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(path)));
                IndexSearcher searcher = new IndexSearcher(reader);
                
                readerMap.put(indexKey, reader);
                searcherMap.put(indexKey, searcher);
                System.out.println("-> Caricato indice: " + indexKey + " da: " + path);
            } catch (IOException e) {
                System.err.println("Errore nel caricamento dell'indice '" + indexKey + "' dal percorso: " + path + ". " + e.getMessage());
            }
        }
    }

    @PreDestroy
    public void destroy() {
        System.out.println("Chiusura di tutti i DirectoryReader...");
        for (DirectoryReader reader : readerMap.values()) {
            try {
                reader.close();
            } catch (IOException e) {
                System.err.println("Errore durante la chiusura del reader: " + e.getMessage());
            }
        }
    }


    //!===============================================================================
    //!                         FUNZIONE DI RICERCA PRINCIPALE
    //!===============================================================================
    public Map<String, List<SearchResult>> search(String queryText, List<String> indicesScelti, String campoScelto) throws Exception {

        //field: campo di ricerca title, ecc
        //querytext: termine cercato

        Map<String, List<SearchResult>> risultatiFinali = new HashMap<>();

        for (String currentIndex : indicesScelti) {
            IndexSearcher currentSearcher = searcherMap.get(currentIndex);


            if (currentSearcher == null) {
                System.err.println("nessun risultato trovato per '" + queryText + "' tra: " + currentIndex);
                continue; 
            }

            Query query = buildQuery(queryText, currentIndex, campoScelto);
            TopDocs hits = currentSearcher.search(query, 10);
            risultatiFinali.put(currentIndex, mapHitsToDTO(hits, currentSearcher, currentIndex));
        }

        return risultatiFinali;
    }


    //!metodo di supporto che costruisce una query specifica per il caso di utilizzo
    private Query buildQuery(String testoRicerca, String index, String campoScelto) throws ParseException {

        //testoRicerca: effettiva ricerca dell'utente
        //index:target della ricerca (file, tabella, immagine)
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
        switch (index.toLowerCase()) {
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
            throw new ParseException("Nessun campo di ricerca predefinito trovato per l'indice: " + index);
        }

        // L'utilizzo dell'istanza risolve l'errore di tipizzazione
        MultiFieldQueryParser multiParser = new MultiFieldQueryParser(defaultFields, analyzer);
        return multiParser.parse(testoRicerca);
    }


    // -------------------------------------------------------------------------
    // HELPER: MAPPATURA RISULTATI (Hits -> DTO)
    // -------------------------------------------------------------------------
    private List<SearchResult> mapHitsToDTO(TopDocs hits, IndexSearcher searcher, String indexKey) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        
        for (ScoreDoc scoreDoc : hits.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            
            String id = doc.get("id"); 
            float score = scoreDoc.score;
            String titolo, snippet, urlDettaglio;
            
            if ("articoli".equals(indexKey)) {
                titolo = doc.get("title");
                
                String abst = doc.get("articleAbstract");
                snippet = (abst != null) ? abst.substring(0, Math.min(abst.length(), 150)) + "..." : "Abstract non disponibile.";
                // Link diretto al file HTML originale
                urlDettaglio = "/raw_articles/" + id;
            } else if ("tabelle".equals(indexKey)) { 
                titolo = doc.get("caption");
                String context = doc.get("context_paragraphs");
                snippet = (context != null) ? context.substring(0, Math.min(context.length(), 150)) + "..." : "Contesto non disponibile.";
                String articleId = doc.get("fileName");
                urlDettaglio = "/dettaglio/tabelle/" + id + "?articleId=" + articleId; 
            } else {
                titolo = doc.get("title") != null ? doc.get("title") : doc.get("id"); 
                snippet = "Dettagli non ancora mappati per questo tipo di indice.";
                urlDettaglio = "/dettaglio/" + indexKey + "/" + id;
            }

            results.add(new SearchResult(indexKey.toUpperCase(), id, titolo, snippet, score, urlDettaglio));
        }
        return results;
    }


    
    // metodo di supporto per la ricerca dettagliata di un documento 
    public Document getDocumentById(String id, String indexKey) throws IOException {
        IndexSearcher targetSearcher = searcherMap.get(indexKey);
        if (targetSearcher == null) {
            throw new IllegalArgumentException("Indice non valido o non caricato: " + indexKey);
        }
        
        Query idQuery = new TermQuery(new Term("id", id));
        
        TopDocs hits = targetSearcher.search(idQuery, 1);

        if (hits.scoreDocs.length > 0) {
            return targetSearcher.storedFields().document(hits.scoreDocs[0].doc);
        }
        return null;
    }










    //!=============== METODI DEPRECATI ===============

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

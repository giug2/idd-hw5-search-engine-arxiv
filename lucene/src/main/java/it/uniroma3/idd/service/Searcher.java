package it.uniroma3.idd.service;

import it.uniroma3.idd.evaluation.*;
import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.dto.*;
import it.uniroma3.idd.service.MetricService;
import it.uniroma3.idd.event.IndexingCompleteEvent;
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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;


@Service
public class Searcher {

    private final Path indexPath;
    private final Analyzer analyzer;

    private final MetricService metricService; 

    private final Map<String, IndexSearcher> searcherMap = new HashMap<>();
    private final Map<String, DirectoryReader> readerMap = new HashMap<>();


    @Value("#{${lucene.indices.map}}")
    private Map<String, String> indexPaths; 

    @Autowired
    public Searcher(LuceneConfig luceneConfig, Analyzer perFieldAnalyzer, MetricService metricService) {
        this.indexPath = Paths.get(luceneConfig.getIndexDirectory());
        this.analyzer = perFieldAnalyzer;
        this.metricService = metricService;
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


    @EventListener
    public void onIndexingComplete(IndexingCompleteEvent event) {
        System.out.println("Ricaricamento indici dopo indicizzazione...");
        
        // Chiudi prima i reader esistenti
        for (DirectoryReader reader : readerMap.values()) {
            try {
                reader.close();
            } catch (IOException e) {
                System.err.println("Errore durante la chiusura del reader: " + e.getMessage());
            }
        }
        readerMap.clear();
        searcherMap.clear();
        
        // Ricarica gli indici
        for (Map.Entry<String, String> entry : indexPaths.entrySet()) {
            String indexKey = entry.getKey();
            String path = entry.getValue();
            
            try {
                DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(path)));
                IndexSearcher searcher = new IndexSearcher(reader);
                
                readerMap.put(indexKey, reader);
                searcherMap.put(indexKey, searcher);
                System.out.println("-> Ricaricato indice: " + indexKey + " da: " + path + " (docs: " + reader.numDocs() + ")");
            } catch (IOException e) {
                System.err.println("Errore nel ricaricamento dell'indice '" + indexKey + "': " + e.getMessage());
            }
        }
        System.out.println("Indici ricaricati con successo!");
    }


    // ===== UTILS ======
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



    // FUNZIONE DI RICERCA PRINCIPALE
    public SearchResponse search(String queryText, List<String> indiceScelti, String campoScelto) throws Exception {
        SearchResponse response = new SearchResponse();
        Map<String, List<SearchResult>> risultatiFinali = new HashMap<>();

        for (String currentIndex : indiceScelti) {
            IndexSearcher currentSearcher = searcherMap.get(currentIndex);

            if (currentSearcher == null) {
                System.err.println("Nessun searcher trovato per l'indice: " + currentIndex);
                continue; 
            }

            long startTime = System.currentTimeMillis();
            Query query = buildQuery(queryText, currentIndex, campoScelto);
            TopDocs hits = currentSearcher.search(query, 10);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Chiamata singola al servizio metriche: salva il risultato nell'oggetto m
            SearchMetrics m = metricService.evaluateSearch(hits, queryText, currentIndex, duration, currentSearcher);
            
            // Aggiungi le metriche alla risposta
            response.getMetrichePerIndice().put(currentIndex, m);

            // Mappa i risultati
            List<SearchResult> currentResults = mapHitsToDTO(hits, currentSearcher, currentIndex);
            risultatiFinali.put(currentIndex, currentResults);
        }
        
        response.setRisultati(risultatiFinali);
        return response;
    }


    // Metodo di supporto che costruisce una query specifica per il caso di utilizzo
    private Query buildQuery(String testoRicerca, String index, String campoScelto) throws ParseException {

        //testoRicerca: effettiva ricerca dell'utente
        //index:target della ricerca (file, tabella, immagine)
        //campoScelto: di default a null

        // Gestione speciale per la ricerca per data (solo per articoli)
        // Sintassi: date:2025, date:2025-08, date:2025-08-12
        if (testoRicerca.toLowerCase().contains("date:")) {
            return buildDateQuery(testoRicerca, index, campoScelto);
        }

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
                defaultFields = new String[]{"caption", "body", "citing_paragraphs", "contextual_paragraphs"};
                break;
            case "figure":
                defaultFields = new String[]{"caption", "citing_paragraphs", "contextual_paragraphs"};
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

    /**
     * Costruisce una query per la ricerca per data.
     * Supporta:
     * - date:2025 (tutti i documenti del 2025)
     * - date:2025-08 (tutti i documenti di agosto 2025)
     * - date:2025-08-12 (documenti esattamente del 12 agosto 2025)
     * Può essere combinata con altre query: "neural network AND date:2025"
     */
    private Query buildDateQuery(String testoRicerca, String index, String campoScelto) throws ParseException {
        // Pattern per estrarre date:YYYY[-MM[-DD]]
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile(
            "date:(\\d{4})(?:-(\\d{2}))?(?:-(\\d{2}))?", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = datePattern.matcher(testoRicerca);
        
        if (!matcher.find()) {
            throw new ParseException("Formato data non valido. Usa: date:YYYY, date:YYYY-MM o date:YYYY-MM-DD");
        }
        
        String year = matcher.group(1);
        String month = matcher.group(2);
        String day = matcher.group(3);
        
        // Costruisce il prefisso della data
        String datePrefix;
        if (day != null) {
            datePrefix = year + "-" + month + "-" + day; // Ricerca esatta per giorno
        } else if (month != null) {
            datePrefix = year + "-" + month; // Tutti i documenti del mese
        } else {
            datePrefix = year; // Tutti i documenti dell'anno
        }
        
        // Crea PrefixQuery per publicationDate
        Query dateQuery = new PrefixQuery(new Term("publicationDate", datePrefix));
        // Rimuovi la parte date:... dalla query per vedere se ci sono altri termini
        String remainingQuery = testoRicerca.replaceAll("(?i)date:\\d{4}(?:-\\d{2})?(?:-\\d{2})?", "").trim();
        // Rimuovi eventuali AND/OR rimasti all'inizio o alla fine
        remainingQuery = remainingQuery.replaceAll("^(AND|OR)\\s+", "").replaceAll("\\s+(AND|OR)$", "").trim();
        
        if (remainingQuery.isEmpty()) {
            // Solo ricerca per data
            return dateQuery;
        }
        
        // Combina ricerca per data con altri termini
        String[] defaultFields;
        switch (index.toLowerCase()) {
            case "articoli":
                defaultFields = new String[]{"title", "authors", "articleAbstract", "paragraphs"};
                break;
            case "tabelle":
                defaultFields = new String[]{"caption", "body", "citing_paragraphs", "contextual_paragraphs"};
                break;
            case "figure":
                defaultFields = new String[]{"caption", "citing_paragraphs", "contextual_paragraphs"};
                break;
            default:
                defaultFields = new String[]{"title"};
                break;
        }
        
        MultiFieldQueryParser multiParser = new MultiFieldQueryParser(defaultFields, analyzer);
        Query textQuery = multiParser.parse(remainingQuery);
        
        // Combina le due query con AND
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(dateQuery, BooleanClause.Occur.MUST);
        builder.add(textQuery, BooleanClause.Occur.MUST);
        
        return builder.build();
    }


    // HELPER: MAPPATURA RISULTATI (Hits -> DTO)
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
                urlDettaglio = "/dettaglio/articoli/" + id;
            } else if ("tabelle".equals(indexKey)) { 
                titolo = doc.get("caption");
                String context = doc.get("contextual_paragraphs");
                snippet = (context != null) ? context.substring(0, Math.min(context.length(), 150)) + "..." : "Contesto non disponibile.";
                String articleId = doc.get("sourceFilename");
                urlDettaglio = "/dettaglio/tabelle/" + id + "?articleId=" + articleId; 
            } else if ("figure".equals(indexKey)) {
                titolo = doc.get("caption");
                String imageUrl = doc.get("imageUrl");
                snippet = (imageUrl != null) ? imageUrl : "URL immagine non disponibile.";
                urlDettaglio = "/dettaglio/figure/" + id;
            } else {
                titolo = doc.get("title") != null ? doc.get("title") : doc.get("id"); 
                snippet = "Dettagli non ancora mappati per questo tipo di indice.";
                urlDettaglio = "/dettaglio/" + indexKey + "/" + id;
            }
            results.add(new SearchResult(indexKey.toUpperCase(), id, titolo, snippet, score, urlDettaglio));
        }
        return results;
    }
}

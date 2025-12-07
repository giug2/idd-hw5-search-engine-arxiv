package it.uniroma3.idd.utils;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.model.Article;
import it.uniroma3.idd.model.ContextualParagraph;
import it.uniroma3.idd.model.Table;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

@Component
public class Parser {

    private final LuceneConfig luceneConfig;

    @Autowired
    public Parser(LuceneConfig luceneConfig) {
        this.luceneConfig = luceneConfig;
    }

    public List<Article> articleParser() {
        // Log the configured articles path for diagnostics
        System.out.println("Configured articles path: " + luceneConfig.getArticlesPath());
        File dir = new File(luceneConfig.getArticlesPath());
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Articles directory not found: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

    // Accept .html case-insensitively (avoid missing files with .HTML)
    File[] files = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".html"));
        if (files == null) {
            System.err.println("Error listing files in: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        System.out.println("Articles directory absolute path: " + dir.getAbsolutePath());
        System.out.println("Number of files in the directory: " + files.length);
        // Print the filenames found (diagnostic)
        for (File f : files) {
            System.out.println(" - found file: " + f.getName());
        }
        List<Article> articles = new ArrayList<>();

        for (File file : files) {
            try {
                Document document = Jsoup.parse(file, "UTF-8");
                String id = file.getName();
                
                // Title - supporta sia arXiv (h1.ltx_title_document) che PubMed (article-title)
                String title = "No Title Found";
                if (document.select("h1.ltx_title_document").first() != null) {
                    // arXiv HTML format
                    title = document.select("h1.ltx_title_document").first().text();
                } else if (document.select("article-title").first() != null) {
                    // PubMed XML format
                    title = document.select("article-title").first().text();
                } else if (document.select("title").first() != null) {
                    // Fallback to HTML title tag
                    title = document.select("title").first().text();
                }
                
                // Authors - supporta sia arXiv che PubMed
                List<String> authors = new ArrayList<>();
                // arXiv format
                document.select("span.ltx_personname").forEach(nameElement -> {
                    authors.add(nameElement.text());
                });
                // PubMed format (fallback)
                if (authors.isEmpty()) {
                    document.select("contrib[contrib-type=author] name").forEach(nameElement -> {
                        String surname = nameElement.select("surname").text();
                        String givenNames = nameElement.select("given-names").text();
                        authors.add(givenNames + " " + surname);
                    });
                }
                
                // Abstract - supporta sia arXiv che PubMed
                String articleAbstract = "No Abstract Found";
                if (document.select("div.ltx_abstract p").first() != null) {
                    // arXiv format
                    articleAbstract = document.select("div.ltx_abstract p").text();
                } else if (document.select("abstract p").first() != null) {
                    // PubMed format
                    articleAbstract = document.select("abstract p").text();
                }
                
                // Date - per ora manteniamo il formato PubMed, arXiv spesso non ha date esplicite
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

                // Paragraphs (Body) - supporta sia arXiv che PubMed
                List<String> paragraphs = new ArrayList<>();
                // arXiv format (paragrafi con classe ltx_p)
                document.select("p.ltx_p").forEach(paragraph -> paragraphs.add(paragraph.text()));
                // PubMed format (fallback)
                if (paragraphs.isEmpty()) {
                    document.select("body p").forEach(paragraph -> paragraphs.add(paragraph.text()));
                }

                Article article = new Article(id, title, authors, paragraphs, articleAbstract, publicationDate);
                articles.add(article);

            } catch (IOException e) {
                System.out.println("Error opening the file: " + file.getName());
                e.printStackTrace();
            }
        }

        return articles;
    }

public List<Table> tableParser() {
    // Usiamo il getter per il path configurato nelle properties
    File dir = new File(luceneConfig.getTablesPath());
    
    // Controlli di sicurezza sulla directory
    if (!dir.exists() || !dir.isDirectory()) {
        System.err.println("Tables directory not found: " + dir.getAbsolutePath());
        return new ArrayList<>();
    }

    // Filtriamo i file JSON
    File[] files = dir.listFiles((dir1, name) -> name.endsWith(".json"));
    if (files == null) {
        System.err.println("Error listing files in: " + dir.getAbsolutePath());
        return new ArrayList<>();
    }

    System.out.println("Number of JSON files found: " + files.length);
    List<Table> tables = new ArrayList<>();

    for (File file : files) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(file);

            // Il JSON è un Oggetto (Mappa): { "S4.T1": { ... }, "S4.T2": { ... } }
            if (!rootNode.isObject()) {
                System.err.println("WARNING: File " + file.getName() + " is NOT a JSON Object. Skipping.");
                continue;
            }

            // Ricaviamo il Paper ID dal nome del file fisico (es. 2509.16375v1)
            String filename = file.getName();
            String paperId = filename.replace("_data.json", "").replace(".json", "");

            int tablesInFile = 0;
            
            // Iteriamo sui campi dell'oggetto JSON
            Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
            
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                
                String partialTableId = entry.getKey(); // Es. "S4.T1"
                JsonNode tableData = entry.getValue();

                // COSTRUZIONE ID UNIVOCO: paperId + "_" + tableId
                String uniqueId = paperId + "_" + partialTableId;

                // --- Estrazione Campi ---
                
                // Titolo del file di origine (dal nuovo campo JSON "source_file")
                // Se manca, usiamo paperId come fallback
                String sourceFilename = tableData.path("source_file").asText(paperId);

                String caption = tableData.path("caption").asText("");
                String bodyHtml = tableData.path("body").asText("");
                
                // Helper per pulire l'HTML per l'indicizzazione
                String bodyCleaned = cleanHtml(bodyHtml);

                // Estrazione Liste
                List<String> mentions = extractStringList(tableData, "citing_paragraphs");
                List<String> terms = extractStringList(tableData, "informative_terms_identified");
                
                // Estrazione Contesto (Solo HTML)
                // Usiamo il metodo helper specifico per appiattire la struttura complessa
                List<String> contextParagraphs = extractContextFromComplexList(tableData, "contextual_paragraphs");
                
                // Conversione da List<String> a List<ContextualParagraph> non necessaria qui 
                // perché abbiamo deciso di passare solo le stringhe HTML al costruttore per semplicità,
                // oppure se il costruttore vuole oggetti complessi, dobbiamo crearli.
                // 
                // NOTA: Nel costruttore Table che abbiamo definito prima, 'contextualParagraphs' era List<ContextualParagraph>.
                // Se vuoi mantenere quella firma, dobbiamo convertire le stringhe in oggetti.
                // Se invece hai semplificato il costruttore per prendere List<String>, lascia così.
                // 
                // Assumo la versione PIÙ SEMPLICE (List<String>) per coerenza con indexTables che fa String.join.
                // Se il costruttore Table richiede List<ContextualParagraph>, dimmelo. 
                //
                // Qui assumo che Table abbia un costruttore che accetta i dati grezzi.
                
                // Creazione Oggetto Table (Ordine argomenti aggiornato al nuovo costruttore)
                Table table = new Table(
                    uniqueId,           // id
                    sourceFilename,     // sourceFilename (NUOVO CAMPO)
                    caption,            // caption
                    bodyHtml,           // body (html grezzo)
                    bodyCleaned,        // bodyCleaned
                    terms,              // informativeTerms
                    mentions,           // citingParagraphs
                    null                // contextualParagraphs (List<Object>). Passiamo null se usiamo le stringhe
                                        // OPPURE: Modifica il costruttore per accettare List<String> contextHtmls
                );
                
                // FIX RAPIDO: Poiché indexTables usa 'getContext_paragraphs()' che ritorna List<String>,
                // Dobbiamo assicurarci che l'oggetto Table sia popolato correttamente.
                // Se Table ha un setter per contextParagraphs che accetta List<String>, usalo:
                // table.setContextParagraphsStrings(contextParagraphs); 
                // 
                // Oppure popola il campo corrispondente se usi una classe custom:
                List<ContextualParagraph> cpList = new ArrayList<>();
                for(String html : contextParagraphs) {
                    cpList.add(new ContextualParagraph(html, null));
                }
                table.setContextualParagraphs(cpList);


                tables.add(table);
                tablesInFile++;
            }
        } catch (IOException e) {
            System.err.println("CRITICAL JSON PARSING ERROR in file: " + file.getName() + ". Message: " + e.getMessage());
        }
    }
    System.out.println("Successfully parsed a total of " + tables.size() + " tables.");
    return tables;
}

// --- METODI HELPER ---

/**
 * Estrae una lista di stringhe da un campo JSON array semplice.
 * Es. ["term1", "term2"] -> List<String>
 */
private List<String> extractStringList(JsonNode parentNode, String fieldName) {
    List<String> resultList = new ArrayList<>();
    JsonNode node = parentNode.path(fieldName); // .path() è più sicuro di .get() (non ritorna null)
    
    if (node.isArray()) {
        node.forEach(element -> {
            String text = element.asText("").trim();
            if (!text.isEmpty()) {
                resultList.add(text);
            }
        });
    }
    return resultList;
}

/**
 * Estrae il campo "html" da una lista di oggetti complessi.
 * JSON Python: "contextual_paragraphs": [ {"html": "<p>...</p>", "matched_terms": []}, ... ]
 * Output Java: List<String> contenente solo gli HTML.
 */
private List<String> extractContextFromComplexList(JsonNode parentNode, String fieldName) {
    List<String> resultList = new ArrayList<>();
    JsonNode node = parentNode.path(fieldName);

    if (node.isArray()) {
        node.forEach(objNode -> {
            // Estraiamo solo il campo "html" dall'oggetto
            if (objNode.has("html")) {
                String htmlContent = objNode.get("html").asText("").trim();
                if (!htmlContent.isEmpty()) {
                    resultList.add(htmlContent);
                }
            }
        });
    }
    return resultList;
}


    public String cleanHtml(String htmlContent) {
        Document doc = Jsoup.parse(htmlContent);
        return doc.text();
    }

}

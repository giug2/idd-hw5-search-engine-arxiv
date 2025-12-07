package it.uniroma3.idd.utils;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.model.Article;
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

// Assumo che la classe sia parte di un Service o Component
public List<Table> tableParser() {
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

            // MODIFICA: Il tuo JSON generato da Python è un Oggetto (Mappa), non un Array.
            // Esempio: { "S4.T1": { ... }, "S4.T2": { ... } }
            if (!rootNode.isObject()) {
                System.err.println("WARNING: File " + file.getName() + " is NOT a JSON Object. Skipping.");
                continue;
            }

            // 1. Ricaviamo il Paper ID dal nome del file
            // Il file si chiama tipo "2509.16375v1_data.json" -> paperId = "2509.16375v1"
            String filename = file.getName();
            String paperId = filename.replace("_data.json", "").replace(".json", "");

            int tablesInFile = 0;
            
            // 2. Iteriamo sui campi dell'oggetto JSON (chiave = TableID parziale, valore = Dati)
            Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
            
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                
                String partialTableId = entry.getKey(); // Es. "S4.T1"
                JsonNode tableData = entry.getValue();

                // COSTRUZIONE ID UNIVOCO: paperId + "_" + tableId (es. 2509.16375v1_S4.T1)
                // Uso underscore per separare paper da tabella
                String uniqueId = paperId + "_" + partialTableId;

                // Estrazione Campi Semplici (con valore di default vuoto)
                String caption = tableData.path("caption").asText("");
                String bodyHtml = tableData.path("body").asText("");
                
                // Nota: htmlBody nel costruttore originale sembrava ridondante o assente nel nuovo JSON, 
                // riutilizziamo bodyHtml o stringa vuota.
                String htmlBody = bodyHtml; 

                // Estrazione Liste di Stringhe
                // Mappatura: "citing_paragraphs" (Python) -> mentions (Java)
                List<String> mentions = extractStringList(tableData, "citing_paragraphs");
                
                // Mappatura: "informative_terms_identified" (Python) -> terms (Java)
                List<String> terms = extractStringList(tableData, "informative_terms_identified");

                // Mappatura COMPLESSA: "contextual_paragraphs" (Python) -> context_paragraphs (Java)
                // Nel JSON Python è una lista di oggetti, in Java vuoi una List<String>.
                // Estraiamo solo il campo "html" dall'oggetto.
                List<String> contextParagraphs = extractContextFromComplexList(tableData, "contextual_paragraphs");

                // Creazione Oggetto Table
                // Assumo che cleanHtml() sia un metodo definito nella tua classe per pulire i tag
                Table table = new Table(
                    uniqueId,                       // id
                    caption,                        // caption
                    bodyHtml,                       // tableHtml (grezzo)
                    cleanHtml(bodyHtml),            // cleanHtml (solo testo per indicizzazione)
                    mentions,                       // mentions
                    contextParagraphs,              // context_paragraphs
                    terms,                          // terms
                    paperId,                        // paperId
                    htmlBody                        // htmlBody
                );

                tables.add(table);
                tablesInFile++;
            }

            if (tablesInFile == 0) {
                 // Può capitare se il JSON è {} (vuoto)
                 // System.out.println("File " + file.getName() + " contained 0 tables (empty object).");
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

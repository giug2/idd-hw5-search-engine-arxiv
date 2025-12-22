package it.uniroma3.idd.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.model.*;


@Component
public class Parser {

    private final LuceneConfig luceneConfig;


    @Autowired
    public Parser(LuceneConfig luceneConfig) {
        this.luceneConfig = luceneConfig;
    }

    // ====== UTILS =======
    private String extractPublicationDate(Document document) {
        // ===== meta citation_date =====
        Element metaDate = document.selectFirst(
            "meta[name=citation_date], meta[name=citation_publication_date]"
        );
        if (metaDate != null) {
            return normalizeArxivDate(metaDate.attr("content"));
        }

        // ===== DC.Date =====
        metaDate = document.selectFirst("meta[name=DC.Date]");
        if (metaDate != null) {
            return normalizeArxivDate(metaDate.attr("content"));
        }

        // ===== testo Submitted on =====
        Element submitted = document.selectFirst("div.ltx_dates, span.ltx_date");
        if (submitted != null) {
            String parsed = parseSubmittedDate(submitted.text());
            if (!parsed.equals("Unknown Date")) {
                return parsed;
            }
        }

        Element pubDate = document.selectFirst("pub-date[pub-type=epub]");
        if (pubDate == null)
            pubDate = document.selectFirst("pub-date[pub-type=ppub]");
        if (pubDate == null)
            pubDate = document.selectFirst("pub-date");

        if (pubDate != null) {
            String year = pubDate.select("year").text();
            String month = pubDate.select("month").text();
            String day = pubDate.select("day").text();

            if (!year.isEmpty()) {
                return year +
                    (!month.isEmpty() ? "-" + normalizeMonth(month) : "") +
                    (!day.isEmpty() ? "-" + day : "");
            }
        }
        // Formato: <!--Generated on Tue Aug 12 22:39:29 2025 by LaTeXML-->
        String html = document.html();
        String parsed = parseGeneratedOnComment(html);
        if (!parsed.equals("Unknown Date")) {
            return parsed;
        }

        return "Unknown Date";
    }


    private String parseGeneratedOnComment(String html) {
        // Cerca: <!--Generated on Day Mon DD HH:MM:SS YYYY by LaTeXML-->
        Pattern p = Pattern.compile("Generated on \\w+ (\\w+)\\s+(\\d{1,2}) [\\d:]+ (\\d{4}) by LaTeXML");
        Matcher m = p.matcher(html);
        if (m.find()) {
            String month = normalizeMonth(m.group(1));
            String day = m.group(2);
            String year = m.group(3);
            if (!month.isEmpty()) {
                return year + "-" + month + "-" + (day.length() == 1 ? "0" + day : day);
            }
        }
        return "Unknown Date";
    }


    private String normalizeArxivDate(String date) {
        // 2019/06/12 → 2019-06-12
        // 2018-04-21 → 2018-04-21
        return date.trim().replace("/", "-");
    }


    private String parseSubmittedDate(String text) {
        // "Submitted on 12 Jun 2019"
        Pattern p = Pattern.compile("(\\d{1,2})\\s+([A-Za-z]+)\\s+(\\d{4})");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String day = m.group(1);
            String month = normalizeMonth(m.group(2));
            String year = m.group(3);
            return year + "-" + month + "-" + (day.length() == 1 ? "0" + day : day);
        }
        return "Unknown Date";
    }


    private String normalizeMonth(String month) {
        if (month == null || month.isEmpty()) return "";
        month = month.toLowerCase().trim();
        switch (month) {
            case "jan":
            case "january": return "01";
            case "feb":
            case "february": return "02";
            case "mar":
            case "march": return "03";
            case "apr":
            case "april": return "04";
            case "may": return "05";
            case "jun":
            case "june": return "06";
            case "jul":
            case "july": return "07";
            case "aug":
            case "august": return "08";
            case "sep":
            case "september": return "09";
            case "oct":
            case "october": return "10";
            case "nov":
            case "november": return "11";
            case "dec":
            case "december": return "12";
            default:
                // numeric month
                if (month.matches("\\d+")) {
                    return month.length() == 1 ? "0" + month : month;
                }
                return "";
        }
    }
    
    
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


    /*===================================
    ============= ARTICOLI ===============
    =====================================*/
    public List<Article> articleParser() {
        System.out.println("Configured articles path: " + luceneConfig.getArticlesPath());
        File dir = new File(luceneConfig.getArticlesPath());
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Articles directory not found: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }
 
        File[] files = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".html"));
        if (files == null) {
            System.err.println("Error listing files in: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        System.out.println("Articles directory absolute path: " + dir.getAbsolutePath());
        System.out.println("Number of files in the directory: " + files.length);

        List<Article> articles = new ArrayList<>();

        for (File file : files) {
            try {
                Document document = Jsoup.parse(file, "UTF-8");
                String id = file.getName();
                
                // Title (h1.ltx_title_document)
                String title = "No Title Found";
                if (document.select("h1.ltx_title_document").first() != null) {
                    title = document.select("h1.ltx_title_document").first().text();
                } else if (document.select("article-title").first() != null) {
                    title = document.select("article-title").first().text();
                } else if (document.select("title").first() != null) {
                    title = document.select("title").first().text();
                }
                
                // Authors
                List<String> authors = new ArrayList<>();
                document.select("span.ltx_personname").forEach(nameElement -> {
                    authors.add(nameElement.text());
                });
                if (authors.isEmpty()) {
                    document.select("contrib[contrib-type=author] name").forEach(nameElement -> {
                        String surname = nameElement.select("surname").text();
                        String givenNames = nameElement.select("given-names").text();
                        authors.add(givenNames + " " + surname);
                    });
                }
                
                // Abstract 
                String articleAbstract = "No Abstract Found";
                if (document.select("div.ltx_abstract p").first() != null) {
                    articleAbstract = document.select("div.ltx_abstract p").text();
                } else if (document.select("abstract p").first() != null) {
                    articleAbstract = document.select("abstract p").text();
                }
                
                // Date 
                String publicationDate = extractPublicationDate(document);

                // Paragraphs (Body)
                List<String> paragraphs = new ArrayList<>();
                document.select("p.ltx_p").forEach(paragraph -> paragraphs.add(paragraph.text()));
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


    /*===================================
    ============= TABELLE ===============
    =====================================*/
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

                // Il JSON è un Oggetto (Mappa)
                if (!rootNode.isObject()) {
                    System.err.println("WARNING: File " + file.getName() + " is NOT a JSON Object. Skipping.");
                    continue;
                }

                // Ricaviamo il Paper ID dal nome del file fisico (es. 2509.16375v1)
                String filename = file.getName();
                String paperId = filename.replace("_data.json", "").replace(".json", "");

                // Iteriamo sui campi dell'oggetto JSON
                Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
                
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    
                    String partialTableId = entry.getKey(); 
                    JsonNode tableData = entry.getValue();

                    // COSTRUZIONE ID UNIVOCO: paperId + "_" + tableId
                    String uniqueId = paperId + "_" + partialTableId;

                    // --- Estrazione Campi ---
                    // Titolo del file di origine (dal nuovo campo JSON "source_file")
                    // Se manca, usiamo paperId come fallback
                    String sourceFilename = tableData.path("source_file").asText(paperId);
                    String caption = tableData.path("caption").asText("");                    
                    // body" nel JSON ora contiene il TESTO PULITO (per indicizzazione e ricerca)
                    String bodyText = tableData.path("body").asText("");                    
                    // "html_code" nel JSON contiene l'HTML GREZZO (per visualizzazione nel frontend)
                    String htmlCode = tableData.path("html_code").asText("");
                    List<String> mentions = extractStringList(tableData, "citing_paragraphs");
                    List<String> contextParagraphs = extractContextFromComplexList(tableData, "contextual_paragraphs");                    
                    // Conversione da List<String> a List<ContextualParagraph>
                    List<ContextualParagraph> cpList = new ArrayList<>();
                    for(String html : contextParagraphs) {
                        cpList.add(new ContextualParagraph(html, null));
                    }
                    
                    Table table = new Table(
                        uniqueId,           // id
                        sourceFilename,     // sourceFilename
                        caption,            // caption
                        bodyText,           // body (TESTO PULITO per ricerca)
                        htmlCode,           // htmlCode (HTML GREZZO per visualizzazione)
                        mentions,           // citingParagraphs
                        cpList              // contextualParagraphs
                    );

                    tables.add(table);
                }
            } catch (IOException e) {
                System.err.println("CRITICAL JSON PARSING ERROR in file: " + file.getName() + ". Message: " + e.getMessage());
            }
        }
        System.out.println("Successfully parsed a total of " + tables.size() + " tables.");
        return tables;
    }


    /*===================================
    ============= IMMAGINI ==============
    =====================================*/
    public List<Figure> figureParser() {
        File dir = new File(luceneConfig.getFiguresPath());
        
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Figures directory not found: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".json"));
        if (files == null) {
            System.err.println("Error listing files in: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        System.out.println("Number of Figure JSON files found: " + files.length);
        List<Figure> figures = new ArrayList<>();

        for (File file : files) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(file);

                if (!rootNode.isObject()) {
                    System.err.println("WARNING: File " + file.getName() + " is NOT a JSON Object. Skipping.");
                    continue;
                }

                // Ricaviamo il Paper ID dal nome del file (es. 2510.12175v3)
                String filename = file.getName();
                String paperId = filename.replace("_figures.json", "").replace(".json", "");

                Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
                
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    
                    String partialFigureId = entry.getKey();
                    JsonNode figureData = entry.getValue();

                    // ID univoco: paperId + "_" + figureId
                    String uniqueId = paperId + "_" + partialFigureId;

                    // Estrazione campi
                    String sourceFilename = figureData.path("source_file").asText(paperId);
                    String imageUrl = figureData.path("image_url").asText("");
                    String caption = figureData.path("caption").asText("");
                    List<String> citingParagraphs = extractStringList(figureData, "citing_paragraphs");
                    List<String> contextParagraphs = extractContextFromComplexList(figureData, "contextual_paragraphs");

                    // Conversione contextParagraphs in List<ContextualParagraph>
                    List<ContextualParagraph> cpList = new ArrayList<>();
                    for (String html : contextParagraphs) {
                        cpList.add(new ContextualParagraph(html, null));
                    }

                    Figure figure = new Figure(
                        uniqueId,
                        sourceFilename,
                        imageUrl,
                        caption,
                        citingParagraphs,
                        cpList
                    );

                    figures.add(figure);
                }
            } catch (IOException e) {
                System.err.println("CRITICAL JSON PARSING ERROR in figure file: " + file.getName() + ". Message: " + e.getMessage());
            }
        }
        System.out.println("Successfully parsed a total of " + figures.size() + " figures.");
        return figures;
    }
}

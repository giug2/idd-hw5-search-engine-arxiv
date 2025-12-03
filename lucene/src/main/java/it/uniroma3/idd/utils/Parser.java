package it.uniroma3.idd.utils;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.model.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public String cleanHtml(String htmlContent) {
        Document doc = Jsoup.parse(htmlContent);
        return doc.text();
    }

}

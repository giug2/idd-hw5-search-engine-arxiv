package it.uniroma3.idd.service;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.event.IndexingCompleteEvent;
import it.uniroma3.idd.model.Article;
import it.uniroma3.idd.model.Table;
import it.uniroma3.idd.utils.Parser;
import jakarta.annotation.PostConstruct;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@Component
public class LuceneIndexer {

    private final LuceneConfig luceneConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final Analyzer perFieldAnalyzer;
    private final Parser parser;

    @Autowired
    public LuceneIndexer(LuceneConfig luceneConfig, ApplicationEventPublisher eventPublisher, Analyzer perFieldAnalyzer, Parser parser) {
        this.luceneConfig = luceneConfig;
        this.eventPublisher = eventPublisher;
        this.perFieldAnalyzer = perFieldAnalyzer;
        this.parser = parser;
    }

    @PostConstruct
    public void init() {
        try {
            // Log to monitor the flow
            System.out.println("Index initialization in progress...");
            if (luceneConfig.isShouldInitializeIndex()) {
                System.out.println("Deleting the index directory...");
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getIndexDirectory())); // svuota l'idex dei documenti prima di indicizzarli nuovamente
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getTableDirectory())); // svuota l'idex delle tabelle prima di indicizzarli nuovamente
                indexArticles(luceneConfig.getIndexDirectory(), Codec.getDefault()); // Initialize the index
                indexTables(luceneConfig.getTableDirectory(), Codec.getDefault());
            }
            System.out.println("Index initialized, publishing event.");
            eventPublisher.publishEvent(new IndexingCompleteEvent(this)); // lancio l'evento "completeIndexing"
            System.out.println("IndexingComplete event published.");
        } catch (Exception e) {
            throw new RuntimeException("Error initializing the index", e);
        }
    }

    public void indexArticles(String Pathdir, Codec codec) throws IOException {
        Path path = Paths.get(Pathdir);
        Directory dir = FSDirectory.open(path);

        IndexWriterConfig config = new IndexWriterConfig(perFieldAnalyzer);

        // Set the codec
        config.setCodec(codec);

        IndexWriter writer = new IndexWriter(dir, config);

        List<Article> articles = parser.articleParser(); 
        System.out.println("Number of articles parsed: " + articles.size());

        for (Article article : articles) {
            Document doc = new Document();
            doc.add(new StringField("id", article.getId(), TextField.Store.YES));
            doc.add(new TextField("title", article.getTitle(), TextField.Store.YES));
            doc.add(new TextField("authors", String.join(" ", article.getAuthors()), TextField.Store.YES));
            doc.add(new TextField("paragraphs", String.join(" ", article.getParagraphs()), TextField.Store.YES));
            doc.add(new TextField("articleAbstract", article.getArticleAbstract(), TextField.Store.YES));
            doc.add(new TextField("publicationDate", article.getPublicationDate(), TextField.Store.YES));
            writer.addDocument(doc);
        }

        writer.commit();
        writer.close();
    }


public void indexTables(String Pathdir, Codec codec) throws Exception {
        Path path = Paths.get(Pathdir);
        Directory dir = FSDirectory.open(path);

        IndexWriterConfig config = new IndexWriterConfig(perFieldAnalyzer);
        config.setCodec(codec);

        IndexWriter writer = new IndexWriter(dir, config);

        // 1. Recupera la lista di oggetti Table dal parser
        List<Table> tables = parser.tableParser();

        for (Table table : tables) {
            Document doc = new Document();

            // --- CAMPI IDENTIFICATIVI (StringField -> Ricerca Esatta) ---

            // ID univoco (es. "2509.16375v1_S4.T1")
            doc.add(new StringField("id", table.getId(), Field.Store.YES));

            // NUOVO CAMPO: ID/Nome del file sorgente (es. "Titolo Articolo" o "2509.16375v1")
            String sourceFile = table.getSourceFilename() != null ? table.getSourceFilename() : "Unknown";
            doc.add(new StringField("sourceFilename", sourceFile, Field.Store.YES));


            // --- CAMPI TESTUALI (TextField -> Ricerca Full-Text Analizzata) ---

            // Caption
            String caption = table.getCaption() != null ? table.getCaption() : "";
            doc.add(new TextField("caption", caption, Field.Store.YES));

            // Body Cleaned (Testo puro della tabella)
            String bodyCleaned = table.getBodyCleaned() != null ? table.getBodyCleaned() : "";
            doc.add(new TextField("body", bodyCleaned, Field.Store.YES));


            // --- GESTIONE LISTE (Join in stringa unica per Lucene) ---

            // Informative Terms (ex "terms")
            String termsString = table.getInformativeTerms() != null ? String.join(" ", table.getInformativeTerms()) : "";
            doc.add(new TextField("informative_terms", termsString, Field.Store.YES));

            // Citing Paragraphs (ex "mentions")
            String citingString = table.getCitingParagraphs() != null ? String.join(" ", table.getCitingParagraphs()) : "";
            doc.add(new TextField("citing_paragraphs", citingString, Field.Store.YES));

            // Contextual Paragraphs (Lista di Oggetti -> Stringa unica)
            // Dobbiamo estrarre il campo 'html' da ogni oggetto ContextualParagraph
            StringBuilder contextSb = new StringBuilder();
            if (table.getContextualParagraphs() != null) {
                for (var cp : table.getContextualParagraphs()) {
                    if (cp.getHtml() != null) {
                        contextSb.append(cp.getHtml()).append(" ");
                    }
                }
            }
            doc.add(new TextField("contextual_paragraphs", contextSb.toString().trim(), Field.Store.YES));


            // --- CAMPI DI STORAGE (StoredField -> Solo visualizzazione) ---

            // HTML Table (Raw Body)
            String htmlBody = table.getBody() != null ? table.getBody() : "";
            doc.add(new StoredField("html_table", htmlBody));

            writer.addDocument(doc);
        }

        writer.commit();
        writer.close();

    }

    public void deleteNonEmptyDirectory(Path directory) throws IOException {
        // Verifica se la directory esiste
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            // Rimuove ricorsivamente i file e le sottocartelle
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);  // Elimina il file
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);  // Elimina la directory dopo aver cancellato i suoi contenuti
                    return FileVisitResult.CONTINUE;
                }
            });
            System.out.println("Directory and its contents deleted.");
        } else {
            System.out.println("Directory does not exist or is not a directory.");
        }
    }

}

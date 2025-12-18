package it.uniroma3.idd.service;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.event.IndexingCompleteEvent;
import it.uniroma3.idd.model.Article;
import it.uniroma3.idd.model.Figure;
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
                System.out.println("Deleting the index directories...");
                // Delete all index directories to prevent duplicate entries
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getIndexDirectory())); // svuota l'idex dei documenti prima di indicizzarli nuovamente
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getTableDirectory())); // svuota l'idex delle tabelle prima di indicizzarli nuovamente
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getFigureDirectory())); // svuota l'idex delle figure prima di indicizzarli nuovamente
                
                indexArticles(luceneConfig.getIndexDirectory(), Codec.getDefault()); // Initialize the index
                indexTables(luceneConfig.getTableDirectory(), Codec.getDefault());
                indexFigures(luceneConfig.getFigureDirectory(), Codec.getDefault());
            }
            eventPublisher.publishEvent(new IndexingCompleteEvent(this)); // lancio l'evento "completeIndexing"
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
            // StringField per la data: non tokenizzata, ricercabile con PrefixQuery
            doc.add(new StringField("publicationDate", article.getPublicationDate(), Field.Store.YES));
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
    // IMPORTANTE: Usa CREATE per sovrascrivere l'indice ed evitare duplicati ogni riavvio
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE); 

    IndexWriter writer = new IndexWriter(dir, config);

    List<Table> tables = parser.tableParser();

    for (Table table : tables) {
        Document doc = new Document();

        // --- ID e SORGENTE ---
        doc.add(new StringField("id", table.getId(), Field.Store.YES));
        
        String sourceFile = table.getSourceFilename() != null ? table.getSourceFilename() : "Unknown";
        doc.add(new StringField("sourceFilename", sourceFile, Field.Store.YES));

        // --- TESTO PULITO (Full Text Search) ---
        
        // Caption
        String caption = table.getCaption() != null ? table.getCaption() : "";
        doc.add(new TextField("caption", caption, Field.Store.YES));

        // Body (ATTENZIONE QUI):
        // Nel nuovo JSON, "body" è già il testo pulito. 
        // Quindi usiamo table.getBody() per riempire il campo Lucene "body".
        // Non serve più getBodyCleaned() se il JSON è già pulito.
        String bodyText = table.getBody() != null ? table.getBody() : "";
        doc.add(new TextField("body", bodyText, Field.Store.YES));


        // --- LISTE ---
        String termsString = table.getInformativeTerms() != null ? String.join(", ", table.getInformativeTerms()) : "";
        doc.add(new TextField("informative_terms", termsString, Field.Store.YES));

        String citingString = table.getCitingParagraphs() != null ? String.join(" ", table.getCitingParagraphs()) : "";
        doc.add(new TextField("citing_paragraphs", citingString, Field.Store.YES));

        StringBuilder contextSb = new StringBuilder();
        if (table.getContextualParagraphs() != null) {
            for (var cp : table.getContextualParagraphs()) {
                if (cp.getHtml() != null) { // Nota: qui usi .getHtml(), assicurati che ContextualParagraph abbia questo metodo
                    contextSb.append(cp.getHtml()).append(" ");
                }
            }
        }
        doc.add(new TextField("contextual_paragraphs", contextSb.toString().trim(), Field.Store.YES));


        // --- HTML GREZZO (Solo per visualizzazione) ---
        
        // HTML Table (ATTENZIONE QUI):
        // Usiamo il NUOVO getter che punta al campo JSON "html_code"
        String htmlContent = table.getHtmlCode() != null ? table.getHtmlCode() : "";
        doc.add(new StoredField("html_table", htmlContent));

        writer.addDocument(doc);
    }

    writer.commit();
    writer.close();
}

    /**
     * Indicizza le figure estratte dagli articoli.
     */
    public void indexFigures(String Pathdir, Codec codec) throws Exception {
        Path path = Paths.get(Pathdir);
        Directory dir = FSDirectory.open(path);

        IndexWriterConfig config = new IndexWriterConfig(perFieldAnalyzer);
        config.setCodec(codec);

        IndexWriter writer = new IndexWriter(dir, config);

        List<Figure> figures = parser.figureParser();

        for (Figure figure : figures) {
            Document doc = new Document();

            // --- CAMPI IDENTIFICATIVI ---
            doc.add(new StringField("id", figure.getId(), Field.Store.YES));

            String sourceFile = figure.getSourceFilename() != null ? figure.getSourceFilename() : "Unknown";
            doc.add(new StringField("sourceFilename", sourceFile, Field.Store.YES));

            // --- URL IMMAGINE (StoredField - non indicizzato, solo memorizzato) ---
            String imageUrl = figure.getImageUrl() != null ? figure.getImageUrl() : "";
            doc.add(new StoredField("imageUrl", imageUrl));

            // --- CAMPI TESTUALI (TextField -> Ricerca Full-Text) ---
            String caption = figure.getCaption() != null ? figure.getCaption() : "";
            doc.add(new TextField("caption", caption, Field.Store.YES));

            // --- GESTIONE LISTE ---
            String termsString = figure.getInformativeTerms() != null ? String.join(", ", figure.getInformativeTerms()) : "";
            doc.add(new TextField("informative_terms", termsString, Field.Store.YES));

            String citingString = figure.getCitingParagraphs() != null ? String.join(" ", figure.getCitingParagraphs()) : "";
            doc.add(new TextField("citing_paragraphs", citingString, Field.Store.YES));

            // Contextual Paragraphs
            StringBuilder contextSb = new StringBuilder();
            if (figure.getContextualParagraphs() != null) {
                for (var cp : figure.getContextualParagraphs()) {
                    if (cp.getHtml() != null) {
                        contextSb.append(cp.getHtml()).append(" ");
                    }
                }
            }
            doc.add(new TextField("contextual_paragraphs", contextSb.toString().trim(), Field.Store.YES));

            writer.addDocument(doc);
        }

        writer.commit();
        writer.close();
        System.out.println("==========================================");
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

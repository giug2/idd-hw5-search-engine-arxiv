package it.uniroma3.idd.service;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.event.IndexingCompleteEvent;
import it.uniroma3.idd.model.Article;
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
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getIndexDirectory())); // Delete the index directory
                indexArticles(luceneConfig.getIndexDirectory(), Codec.getDefault()); // Initialize the index
            }
            System.out.println("Index initialized, publishing event.");
            eventPublisher.publishEvent(new IndexingCompleteEvent(this)); // Publish the event upon completion
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

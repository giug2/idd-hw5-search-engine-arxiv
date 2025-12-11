package it.uniroma3.idd.service;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.dto.SearchResult;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class Searcher {
    private final Path indexPath;
    private final Analyzer analyzer;

    @Autowired
    public Searcher(LuceneConfig luceneConfig, Analyzer perFieldAnalyzer) {
        this.indexPath = Paths.get(luceneConfig.getIndexDirectory());
        this.analyzer = perFieldAnalyzer;
    }

    public List<SearchResult> search(String field, String queryText) throws Exception {
        List<SearchResult> resultsList = new ArrayList<>();

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
        }

        return resultsList;
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

package it.uniroma3.idd.service;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class DocumentService {

    private final Searcher searcher;

    @Autowired
    public DocumentService(Searcher searcher) {
        this.searcher = searcher;
    }

    public Document getDocument(Long id) {
        //TODO LUCENE
        return null;
    }

    public List<Document> getDocumentsQuery(Map<String,String> filters, Float tresholdMultiplier) throws ParseException, InvalidTokenOffsetsException, IOException {
        String field = "paragraphs"; // default
        String query = "";

        if (filters.containsKey("title")) {
            field = "title";
            query = filters.get("title");
        } else if (filters.containsKey("authors")) {
            field = "authors";
            query = filters.get("authors");
        } else if (filters.containsKey("articleAbstract")) {
            field = "articleAbstract";
            query = filters.get("articleAbstract");
        } else if (filters.containsKey("allFields")) {
            field = "paragraphs";
            query = filters.get("allFields");
        }

        try {
            return searcher.searchDocuments(field, query);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public Document getAllDocuments() {
        //TODO LUCENE
        return null;
    }

}
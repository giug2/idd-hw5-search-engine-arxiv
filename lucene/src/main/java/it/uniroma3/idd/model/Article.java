package it.uniroma3.idd.model;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;

import java.util.List;

public class Article {

    @Id
    @NotNull
    private String id;               

    @NotNull
    private String title;            

    private List<String> authors;    
    private List<String> paragraphs; 
    private String articleAbstract;
    private String publicationDate;

    // Constructor
    public Article(String id, String title, List<String> authors, List<String> paragraphs, String articleAbstract, String publicationDate) {
        this.id = id;
        this.title = title;
        this.authors = authors;
        this.paragraphs = paragraphs;
        this.articleAbstract = articleAbstract;
        this.publicationDate = publicationDate;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

    public List<String> getParagraphs() {
        return paragraphs;
    }

    public void setParagraphs(List<String> paragraphs) {
        this.paragraphs = paragraphs;
    }

    public String getArticleAbstract() {
        return articleAbstract;
    }

    public void setArticleAbstract(String articleAbstract) {
        this.articleAbstract = articleAbstract;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    @Override
    public String toString() {
        return "Article{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", authors=" + authors +
                ", paragraphs=" + paragraphs +
                '}';
    }
}
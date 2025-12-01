package it.uniroma3.idd.dto;


public class SearchResult {
    private final String fileName;
    private final String title;
    private final String publicationDate;
    private final float score;

    public SearchResult(String fileName, String title, String publicationDate, float score) {
        this.fileName = fileName;
        this.title = title;
        this.publicationDate = publicationDate;
        this.score = score;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public float getScore() {
        return score;
    }
}

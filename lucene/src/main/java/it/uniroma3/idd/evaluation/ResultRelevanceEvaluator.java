package it.uniroma3.idd.evaluation;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import java.util.*;
import java.util.stream.Collectors;


public class ResultRelevanceEvaluator {

    private static final CharArraySet LUCENE_STOP_WORDS = EnglishAnalyzer.getDefaultStopSet();


    public enum RelevanceLevel {
        NOT_RELEVANT(0), RELEVANT(1), HIGHLY_RELEVANT(2);
        public final int value;
        RelevanceLevel(int value) { this.value = value; }
    }


    public static RelevanceLevel evaluate(String query, Document doc, String indexKey) {
        if (query == null || doc == null) return RelevanceLevel.NOT_RELEVANT;

        Set<String> queryTokens = tokenize(query);
        String tipo = indexKey.toLowerCase();

        String titolo = "";
        String contenutoSpecifico = ""; 
        double soglia;

        switch (tipo) {
            case "articoli":
                titolo = Optional.ofNullable(doc.get("title")).orElse("");
                // Usiamo SOLO l'abstract. I paragraphs sono troppo lunghi e creano falsi positivi.
                contenutoSpecifico = Optional.ofNullable(doc.get("articleAbstract")).orElse("");
                soglia = 0.85; // Molto severo
                break;
            case "tabelle":
                titolo = Optional.ofNullable(doc.get("caption")).orElse("");
                contenutoSpecifico = Optional.ofNullable(doc.get("body")).orElse("") + " " +
                                     Optional.ofNullable(doc.get("contextual_paragraphs")).orElse("");;
                soglia = 0.60; 
                break;
            case "figure":
            case "immagini":
                titolo = Optional.ofNullable(doc.get("caption")).orElse("");
                contenutoSpecifico = Optional.ofNullable(doc.get("contextual_paragraphs")).orElse("");; 
                soglia = 0.60; 
                break;
            default:
                titolo = Optional.ofNullable(doc.get("title")).orElse("");
                soglia = 0.60;
        }

        // 1. HIGHLY RELEVANT: Match perfetto nel titolo/caption
        if (containsAllTokens(titolo, queryTokens)) {
            return RelevanceLevel.HIGHLY_RELEVANT;
        }

        // 2. RELEVANT: Match alto nel contenuto specifico (senza rumore dei paragrafi)
        if (!contenutoSpecifico.isEmpty() && partialMatch(contenutoSpecifico, queryTokens, soglia)) {
            return RelevanceLevel.RELEVANT;
        }
        return RelevanceLevel.NOT_RELEVANT;
    }


    private static Set<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return Set.of();
        return Arrays.stream(text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
                .filter(t -> t.length() >= 2)
                .filter(t -> !LUCENE_STOP_WORDS.contains(t))
                .collect(Collectors.toSet());
    }


    private static boolean containsAllTokens(String text, Set<String> tokens) {
        if (text == null || text.isEmpty() || tokens.isEmpty()) return false;
        Set<String> textTokens = tokenize(text);
        return textTokens.containsAll(tokens);
    }


    private static boolean partialMatch(String text, Set<String> tokens, double threshold) {
        Set<String> textTokens = tokenize(text);
        long common = tokens.stream().filter(textTokens::contains).count();
        double ratio = (double) common / tokens.size();
        return ratio >= threshold;
    }
}

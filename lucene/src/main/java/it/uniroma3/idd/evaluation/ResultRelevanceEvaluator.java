package it.uniroma3.idd.evaluation;

import org.apache.lucene.document.Document;
import java.util.*;
import java.util.stream.Collectors;


public class ResultRelevanceEvaluator {

    public enum RelevanceLevel {
        NOT_RELEVANT(0),
        RELEVANT(1),
        HIGHLY_RELEVANT(2);

        public final int value;

        RelevanceLevel(int value) {
            this.value = value;
        }
    }


    /**
     * Valuta la rilevanza di un documento Lucene rispetto alla query.
     */
    public static RelevanceLevel evaluate(String query, Document doc, String indexKey) {
        Set<String> queryTokens = tokenize(query);
        String tipo = indexKey.toLowerCase();

        String titolo = "";
        String body = "";

        //mapping dei campi lucene
        switch (tipo) {
            case "articoli":
                titolo = doc.get("title");
                body = doc.get("articleAbstract") + " " + doc.get("paragraphs");
                break;
            case "tabelle":
                titolo = doc.get("caption");
                // Per le tabelle, il 'body' o il contesto sono ottimi per il partial match
                body = doc.get("body") + " " + doc.get("contextual_paragraphs");
                break;
            case "immagini":
            case "figure": // Gestiamo entrambi i casi per sicurezza
                titolo = doc.get("caption");
                body = doc.get("contextual_paragraphs");
                break;
            default:
                titolo = doc.get("title");
        }

        // ==========================
        // MATCH FORTE (Titolo/Caption contiene TUTTI i token) -> Rilevanza 2
        // ==========================
        if (containsAllTokens(titolo, queryTokens)) {
            return RelevanceLevel.HIGHLY_RELEVANT;
        }

        // ==========================
        // MATCH MEDIO (Il corpo contiene ALMENO META' dei token) -> Rilevanza 1
        // ==========================
        if (partialMatch(body, queryTokens)) {
            return RelevanceLevel.RELEVANT;
        }

        return RelevanceLevel.NOT_RELEVANT;
    }


    /* =======================
       === Utility methods ===
       ======================= */

    private static Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        // Tokenizza, rimuove caratteri speciali, converte in lowercase e filtra parole corte (<3 char)
        return Arrays.stream(text.toLowerCase()
                        .replaceAll("[^a-z0-9 ]", " ")
                        .split("\\s+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toSet());
    }


    private static boolean containsAllTokens(String text, Set<String> tokens) {
        if (text == null || text.isEmpty() || tokens.isEmpty()) return false;
        Set<String> textTokens = tokenize(text);
        return textTokens.containsAll(tokens);
    }
    

    private static boolean partialMatch(String text, Set<String> tokens) {
        if (text == null || text.isEmpty() || tokens.isEmpty()) return false;
        Set<String> textTokens = tokenize(text);

        long common = tokens.stream()
                .filter(textTokens::contains)
                .count();

        // Rilevante se contiene almeno la metà dei token cercati (minimo 1)
        return common >= Math.max(1, tokens.size() / 2);
    }
}

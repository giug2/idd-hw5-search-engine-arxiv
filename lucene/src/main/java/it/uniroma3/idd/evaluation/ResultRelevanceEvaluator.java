package it.uniroma3.idd.evaluation;

import org.apache.lucene.document.Document;
import java.util.*;
import java.util.stream.Collectors;

public class ResultRelevanceEvaluator {

    // Set di stop words comuni per pulire la query senza basarsi solo sulla lunghezza
    private static final Set<String> STOP_WORDS = Set.of(
        "il", "lo", "la", "i", "gli", "le", "un", "uno", "una", 
        "di", "a", "da", "in", "con", "su", "per", "tra", "fra",
        "the", "of", "and", "for", "with", "from", "on", "at"
    );

    public enum RelevanceLevel {
        NOT_RELEVANT(0),
        RELEVANT(1),
        HIGHLY_RELEVANT(2);

        public final int value;
        RelevanceLevel(int value) { this.value = value; }
    }

    /**
     * Valuta la rilevanza basandosi sul tipo di indice e soglie dinamiche.
     */
    public static RelevanceLevel evaluate(String query, Document doc, String indexKey) {
        if (query == null || doc == null) return RelevanceLevel.NOT_RELEVANT;

        Set<String> queryTokens = tokenize(query);
        String tipo = indexKey.toLowerCase();

        // 1. Estrazione campi con fallback di sicurezza
        String titolo = "";
        String body = "";
        double sogliaRilevanza;

        switch (tipo) {
            case "articoli":
                titolo = Optional.ofNullable(doc.get("title")).orElse("");
                body = Optional.ofNullable(doc.get("articleAbstract")).orElse("") + " " + 
                       Optional.ofNullable(doc.get("paragraphs")).orElse("");
                sogliaRilevanza = 0.80; // Severo: gli articoli devono essere molto pertinenti
                break;
            case "tabelle":
                titolo = Optional.ofNullable(doc.get("caption")).orElse("");
                body = Optional.ofNullable(doc.get("body")).orElse("") + " " + 
                       Optional.ofNullable(doc.get("contextual_paragraphs")).orElse("");
                sogliaRilevanza = 0.70; // Medio
                break;
            case "figure":
            case "immagini":
                titolo = Optional.ofNullable(doc.get("caption")).orElse("");
                body = Optional.ofNullable(doc.get("contextual_paragraphs")).orElse("");
                sogliaRilevanza = 0.660; // Più flessibile: le caption sono brevi
                break;
            default:
                titolo = Optional.ofNullable(doc.get("title")).orElse("");
                sogliaRilevanza = 0.70;
        }

        // 2. MATCH ALTO (Titolo/Caption contiene TUTTI i token)
        if (containsAllTokens(titolo, queryTokens)) {
            return RelevanceLevel.HIGHLY_RELEVANT;
        }

        // 3. MATCH MEDIO (Body/Contesto con soglia dinamica)
        if (partialMatch(body, queryTokens, sogliaRilevanza)) {
            return RelevanceLevel.RELEVANT;
        }

        return RelevanceLevel.NOT_RELEVANT;
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return Set.of();
        
        return Arrays.stream(text.toLowerCase()
                        .replaceAll("[^a-z0-9 ]", " ")
                        .split("\\s+"))
                .filter(t -> t.length() >= 2) // Teniamo "AI", "ML", "3D"
                .filter(t -> !STOP_WORDS.contains(t)) // Rimuoviamo il rumore
                .collect(Collectors.toSet());
    }

    private static boolean containsAllTokens(String text, Set<String> tokens) {
        if (text == null || text.isEmpty() || tokens.isEmpty()) return false;
        Set<String> textTokens = tokenize(text);
        return textTokens.containsAll(tokens);
    }

    private static boolean partialMatch(String text, Set<String> tokens, double threshold) {
        if (text == null || text.isEmpty() || tokens.isEmpty()) return false;
        
        Set<String> textTokens = tokenize(text);
        long common = tokens.stream().filter(textTokens::contains).count();
        
        double ratio = (double) common / tokens.size();
        return ratio >= threshold;
    }
}
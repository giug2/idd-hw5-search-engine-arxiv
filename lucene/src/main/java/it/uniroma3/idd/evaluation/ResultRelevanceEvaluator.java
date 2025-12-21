package it.uniroma3.idd.evaluation;

import java.util.*;
import it.uniroma3.idd.dto.SearchResult;
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

    public static RelevanceLevel evaluate(
            String query,
            SearchResult result,
            List<String> tableHeaders   // null per articoli/immagini
    ) {

        Set<String> queryTokens = tokenize(query);
        String tipo = result.getTipo().toLowerCase();

        String titolo = result.getTitolo();
        String snippet = result.getSnippet();

        // ==========================
        // MATCH FORTE (dipende dal tipo)
        // ==========================
        if (tipo.equals("article")) {
            if (containsAllTokens(titolo, queryTokens)) {
                return RelevanceLevel.HIGHLY_RELEVANT;
            }
        }

        if (tipo.equals("table")) {
            if (containsAllTokens(titolo, queryTokens) ||
                containsAllTokens(tableHeaders, queryTokens)) {
                return RelevanceLevel.HIGHLY_RELEVANT;
            }
        }

        if (tipo.equals("image")) {
            if (containsAllTokens(titolo, queryTokens)) {
                return RelevanceLevel.HIGHLY_RELEVANT;
            }
        }

        // ==========================
        // MATCH MEDIO
        // ==========================
        if (partialMatch(snippet, queryTokens)) {
            return RelevanceLevel.RELEVANT;
        }

        return RelevanceLevel.NOT_RELEVANT;
    }

    /* =======================
       === Utility methods ===
       ======================= */

    private static Set<String> tokenize(String text) {
        if (text == null) return Set.of();

        return Arrays.stream(text.toLowerCase()
                        .replaceAll("[^a-z0-9 ]", " ")
                        .split("\\s+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toSet());
    }

    private static boolean containsAllTokens(String text, Set<String> tokens) {
        if (text == null || text.isEmpty()) return false;
        Set<String> textTokens = tokenize(text);
        return textTokens.containsAll(tokens);
    }

    private static boolean containsAllTokens(List<String> texts, Set<String> tokens) {
        if (texts == null || texts.isEmpty()) return false;
        return texts.stream().anyMatch(t -> containsAllTokens(t, tokens));
    }

    private static boolean partialMatch(String text, Set<String> tokens) {
        if (text == null || text.isEmpty()) return false;
        Set<String> textTokens = tokenize(text);

        long common = tokens.stream()
                .filter(textTokens::contains)
                .count();

        return common >= Math.max(1, tokens.size() / 2);
    }
}

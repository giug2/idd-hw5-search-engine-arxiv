package it.uniroma3.idd.evaluation;

import java.util.*;
import it.uniroma3.idd.evaluation.ResultRelevanceEvaluator.*;
import it.uniroma3.idd.dto.SearchResult;

public class Evaluator {

    /**
     * Valuta un insieme di query e restituisce MRR medio.
     */
    public static double evaluateQueries(
            List<String> queries,
            Map<String, List<SearchResult>> resultsPerQuery,
            int k
    ) {

        double sumReciprocalRank = 0.0;
        int count = 0;

        for (String query : queries) {

            List<SearchResult> results = resultsPerQuery.get(query);
            if (results == null) continue;

            // Map degli ID (String) → rilevanza
            Map<String, Integer> relevanceMap = new HashMap<>();
            List<String> ranking = new ArrayList<>();

            for (SearchResult result : results) {
                int relevance = ResultRelevanceEvaluator.evaluate(query, result, null).value;
                relevanceMap.put(result.getIdUnivoco(), relevance);
                ranking.add(result.getIdUnivoco());
            }

            // Usa il metodo già esistente in EvaluationMetrics
            double rr = EvaluationMetrics.reciprocalRank(ranking, relevanceMap);
            sumReciprocalRank += rr;
            count++;
        }

        return count > 0 ? sumReciprocalRank / count : 0.0;
    }
}

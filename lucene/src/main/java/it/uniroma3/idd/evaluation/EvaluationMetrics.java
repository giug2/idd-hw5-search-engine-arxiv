package it.uniroma3.idd.evaluation;

import java.util.*;


public class EvaluationMetrics {

    // Calcola il Reciprocal Rank per una singola lista di risultati
    public static double reciprocalRank(List<String> rankedIds, Map<String, Integer> relevanceMap) {
        for (int i = 0; i < rankedIds.size(); i++) {
            // Se troviamo un documento rilevante (>0), ritorniamo 1/(posizione+1)
            if (relevanceMap.getOrDefault(rankedIds.get(i), 0) > 0) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }


    // Calcola la Precision@K
    public static double precisionAtK(List<String> rankedIds, Map<String, Integer> relevanceMap, int k) {
        int relevantCount = 0;
        int limit = Math.min(k, rankedIds.size());
        
        for (int i = 0; i < limit; i++) {
            if (relevanceMap.getOrDefault(rankedIds.get(i), 0) > 0) {
                relevantCount++;
            }
        }
        return (double) relevantCount / k;
    }


    public static double dcg(List<String> rankedIds, Map<String, Integer> relevanceMap, int k) {
        double dcg = 0.0;
        for (int i = 0; i < Math.min(k, rankedIds.size()); i++) {
            int rel = relevanceMap.getOrDefault(rankedIds.get(i), 0);
            dcg += (Math.pow(2, rel) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        return dcg;
    }


    public static double idcg(Map<String, Integer> relevanceMap, int k) {
        List<Integer> rels = new ArrayList<>(relevanceMap.values());
        rels.sort(Collections.reverseOrder()); // Ordina per rilevanza massima ideale

        double idcg = 0.0;
        for (int i = 0; i < Math.min(k, rels.size()); i++) {
            int rel = rels.get(i);
            idcg += (Math.pow(2, rel) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        return idcg;
    }


    public static double ndcg(List<String> rankedIds, Map<String, Integer> relevanceMap, int k) {
        double dcgVal = dcg(rankedIds, relevanceMap, k);
        double idcgVal = idcg(relevanceMap, k);
        return idcgVal == 0.0 ? 0.0 : dcgVal / idcgVal;
    }
}

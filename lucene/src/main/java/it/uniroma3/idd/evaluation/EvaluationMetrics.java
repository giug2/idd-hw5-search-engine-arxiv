package it.uniroma3.idd.evaluation;

import java.util.*;

public class EvaluationMetrics {

    public static double reciprocalRank(
            List<String> rankedIds,
            Map<String, Integer> relevanceMap) {

        for (int i = 0; i < rankedIds.size(); i++) {
            if (relevanceMap.getOrDefault(rankedIds.get(i), 0) > 0) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    public static double dcg(
            List<String> rankedIds,
            Map<String, Integer> relevanceMap,
            int k) {

        double dcg = 0.0;
        for (int i = 0; i < Math.min(k, rankedIds.size()); i++) {
            int rel = relevanceMap.getOrDefault(rankedIds.get(i), 0);
            dcg += (Math.pow(2, rel) - 1) /
                   (Math.log(i + 2) / Math.log(2));
        }
        return dcg;
    }

    public static double idcg(
            Map<String, Integer> relevanceMap,
            int k) {

        List<Integer> rels = new ArrayList<>(relevanceMap.values());
        rels.sort(Collections.reverseOrder());

        double idcg = 0.0;
        for (int i = 0; i < Math.min(k, rels.size()); i++) {
            int rel = rels.get(i);
            idcg += (Math.pow(2, rel) - 1) /
                    (Math.log(i + 2) / Math.log(2));
        }
        return idcg;
    }

    public static double ndcg(
            List<String> rankedIds,
            Map<String, Integer> relevanceMap,
            int k) {

        double dcgVal = dcg(rankedIds, relevanceMap, k);
        double idcgVal = idcg(relevanceMap, k);
        return idcgVal == 0.0 ? 0.0 : dcgVal / idcgVal;
    }
}

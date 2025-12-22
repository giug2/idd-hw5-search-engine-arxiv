package it.uniroma3.idd.service;

import it.uniroma3.idd.evaluation.EvaluationMetrics;
import it.uniroma3.idd.evaluation.ResultRelevanceEvaluator;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;


@Service
public class MetricService {

    /*Valuta la ricerca usando le classi di supporto esterne*/
    public void evaluateSearch(TopDocs topDocs, String query, String indexName, long searchTimeMs, IndexSearcher searcher) {
        
        // Controllo compatibile con tutte le versioni di Lucene
        if (topDocs.scoreDocs.length == 0) {
            System.out.println("METRICS [" + indexName + "]: Nessun risultato per '" + query + "'");
            return;
        }

        List<String> rankedIds = new ArrayList<>();
        Map<String, Integer> relevanceMap = new HashMap<>();

        // calcolo la rilevanza per i diversi risultati
        for (ScoreDoc sd : topDocs.scoreDocs) {
            try {
                Document doc = searcher.storedFields().document(sd.doc);
                String docId = doc.get("id");
                if(docId == null) docId = String.valueOf(sd.doc);

                int relevance = ResultRelevanceEvaluator.evaluate(query, doc, indexName).value;

                rankedIds.add(docId);
                relevanceMap.put(docId, relevance);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //calcolo le diverse metriche inerenti alla query dell'utente
        int k = 10; // Calcoliamo @10
        double ndcg = EvaluationMetrics.ndcg(rankedIds, relevanceMap, k);
        double rr = EvaluationMetrics.reciprocalRank(rankedIds, relevanceMap);
        double precision = EvaluationMetrics.precisionAtK(rankedIds, relevanceMap, k);

        //stampo su console le metriche calcolate
        System.out.println("=====================================");
        System.out.println(" Query:   \"" + query + "\"");
        System.out.println(" Indice:  " + indexName);
        
        String totalHitsStr = String.valueOf(topDocs.totalHits);
        System.out.println(" Results: " + totalHitsStr); 
        
        System.out.println(" Time:    " + searchTimeMs + " ms");
        System.out.printf(" NDCG@%d: %.3f%n", k, ndcg);
        System.out.printf(" RR:      %.3f%n", rr);
        System.out.printf(" P@%d:    %.3f%n", k, precision);
        System.out.println("=====================================");
    }
}

package it.uniroma3.idd.service;

import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


@Service
public class StatsService {

    public void statsIndex(Path indexPath, String indexName) {
        System.out.println("--- STATISTICHE INDICE: " + indexName.toUpperCase() + " ---");

        try (Directory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {

            int numDocs = reader.numDocs();
            System.out.println("Numero di documenti indicizzati: " + numDocs);
            System.out.println("\nConteggio dei termini per ciascun campo:\n");

            Map<String, Long> globalTermCounts = new HashMap<>();

            // Itera su tutti i segmenti (Lucene 10 non permette un singolo LeafReader aggregato)
            for (LeafReaderContext leafCtx : reader.leaves()) {

                LeafReader leafReader = leafCtx.reader();
                FieldInfos fieldInfos = leafReader.getFieldInfos();

                for (FieldInfo fi : fieldInfos) {
                    String fieldName = fi.name;

                    Terms terms = leafReader.terms(fieldName);
                    if (terms == null)
                        continue;

                    long count = 0;
                    TermsEnum te = terms.iterator();
                    while (te.next() != null) {
                        count++;
                    }
                    globalTermCounts.merge(fieldName, count, Long::sum);
                }
            }

            // Output delle statistiche aggregate
            if (globalTermCounts.isEmpty()) {
                System.out.println("Nessun termine trovato.");
            } else {
                for (Map.Entry<String, Long> entry : globalTermCounts.entrySet()) {
                    System.out.println("- Campo: " + entry.getKey()
                            + " - Termini unici (totale): " + entry.getValue());
                }
            }

        } catch (IOException e) {
            System.err.println("Errore durante la lettura dell'indice " + indexName + ": " + e.getMessage());
        }
        System.out.println("----------------------------------------\n");
    }
}

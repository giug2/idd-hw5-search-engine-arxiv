package it.uniroma3.idd.service;

import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class StatsService {
    public void statsIndex(Path indexPath) {
        try (Directory directory = FSDirectory.open(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            int numDocs = reader.numDocs();
            System.out.println("Numero di documenti indicizzati: " + numDocs);
            System.out.println("\nConteggio dei termini per ciascun campo:\n");

            for (LeafReaderContext leafContext : reader.leaves()) {
                LeafReader leafReader = leafContext.reader();

                for (FieldInfo fieldInfo : leafReader.getFieldInfos()) {
                    String fieldName = fieldInfo.name;
                    Terms terms = leafReader.terms(fieldName);

                    if (terms != null) {
                        TermsEnum termsEnum = terms.iterator();
                        int termCount = 0;

                        while (termsEnum.next() != null) {
                            termCount++;
                        }

                        System.out.println("- Campo: " + fieldName +
                                " - Termini indicizzati: " + termCount);
                    } else {
                        System.out.println("- Campo: " + fieldName +
                                " - Nessun termine trovato.");
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Errore durante la lettura dell'indice: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

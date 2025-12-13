package it.uniroma3.idd.service;

import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef; // Import necessario per l'iteratore
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class StatsService {
    public void statsIndex(Path articlesPath, Path tablesPath) {

        System.out.println("---------- documenti");
        /*----------------------------------statistiche file----------------------------------*/
        try (Directory directory = FSDirectory.open(articlesPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            int numFile = reader.numDocs();
            System.out.println("Numero di documenti indicizzati: " + numFile);
            System.out.println("\nConteggio dei termini per ciascun campo:");

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

        /*----------------------------------statistiche tabelle----------------------------------*/
        System.out.println("\n---------- tabelle");

        try (Directory directory = FSDirectory.open(tablesPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            int numTables = reader.numDocs();
            System.out.println("Numero totale di tabelle trovate: " + numTables);
            System.out.println("\nConteggio dei termini per ciascun campo:");

            // Mappa per accumulare i totali da tutti i segmenti (così non si ripetono le stampe)
            Map<String, Long> fieldTermCounts = new HashMap<>();

            // 1. Accumulo dei dati
            for (LeafReaderContext leafContext : reader.leaves()) {
                LeafReader leafReader = leafContext.reader();

                for (FieldInfo fieldInfo : leafReader.getFieldInfos()) {
                    String fieldName = fieldInfo.name;
                    Terms terms = leafReader.terms(fieldName);

                    long segmentCount = 0;
                    if (terms != null) {
                        // Contiamo i termini esattamente come fai sopra
                        TermsEnum termsEnum = terms.iterator();
                        while (termsEnum.next() != null) {
                            segmentCount++;
                        }
                    }
                    
                    // Sommo al totale esistente per quel campo
                    fieldTermCounts.put(fieldName, fieldTermCounts.getOrDefault(fieldName, 0L) + segmentCount);
                }
            }

            // 2. Stampa dei risultati (Stesso stile della sezione articoli)
            for (Map.Entry<String, Long> entry : fieldTermCounts.entrySet()) {
                String fieldName = entry.getKey();
                long count = entry.getValue();

                if (count > 0) {
                    System.out.println("- Campo: " + fieldName + " - Termini indicizzati: " + count);
                } else {
                    System.out.println("- Campo: " + fieldName + " - Nessun termine trovato.");
                }
            }

        } catch (IOException e) {
            System.err.println("Errore durante la lettura dell'indice delle tabelle: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

/*
tabella di conversione termini tabelle 

json                                -->      variabile java             -->      campo lucene

(Chiave dell'oggetto) S3.T1	        -->      tableId	                -->      id
source_file	                        -->      sourceFile	                -->      sourceFilename
caption	                            -->      caption	                -->      caption
informative_terms_identified	    -->      informativeTerms	        -->      informative_terms
citing_paragraphs	                -->      citingParagraphs	        -->      citing_paragraphs
contextual_paragraphs	            -->      contextualParagraphs	    -->      contextual_paragraphs
*/
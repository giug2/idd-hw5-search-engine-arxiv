package it.uniroma3.idd.event;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import java.nio.file.Paths;
import java.nio.file.Path;


@Component
public class IndexListener implements ApplicationListener<IndexingCompleteEvent> {

    private final LuceneConfig luceneConfig;
    private final StatsService statsService;


    @Autowired
    public IndexListener(LuceneConfig luceneConfig, StatsService statsService) {
        this.luceneConfig = luceneConfig;
        this.statsService = statsService;
    }


    @Override
    public void onApplicationEvent(@NonNull IndexingCompleteEvent event) {
        System.err.println("------- AVVIO STATISTICHE ------");

        // Ottiene il Path per l'indice degli Articoli
        Path articlesIndexPath = Paths.get(luceneConfig.getIndexDirectory());
        
        // Esegue le statistiche per gli Articoli
        statsService.statsIndex(articlesIndexPath, "ARTICOLI"); 

        // Ottiene il Path per l'indice delle Tabelle
        // Usa il nuovo campo 'tableDirectory' della configurazione
        Path tablesIndexPath = Paths.get(luceneConfig.getTableDirectory());
        
        // Esegue le statistiche per le Tabelle
        statsService.statsIndex(tablesIndexPath, "TABELLE");

        // Ottiene il Path per l'indice delle Immagini
        // Usa il nuovo campo 'imageDirectory' della configurazione
        Path imageIndexPath = Paths.get(luceneConfig.getFigureDirectory());
        
        // Esegue le statistiche per le Tabelle
        statsService.statsIndex(imageIndexPath, "IMMAGINI");
        
        System.err.println("--------------------------------");
    }
}

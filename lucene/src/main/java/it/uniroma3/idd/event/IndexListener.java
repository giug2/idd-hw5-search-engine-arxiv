package it.uniroma3.idd.event;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;

import java.nio.file.Paths;

@Component
public class IndexListener implements ApplicationListener<IndexingCompleteEvent> {

    private final LuceneConfig luceneConfig;

    @Autowired
    public IndexListener(LuceneConfig luceneConfig) {
        this.luceneConfig = luceneConfig;
    }
    @Override
    public void onApplicationEvent(@NonNull IndexingCompleteEvent event) {
        System.err.println("\n\n=========== INIZIO SEZIONE STATISTICHE===========\n");
        StatsService statistiche = new StatsService();
        statistiche.statsIndex(Paths.get(luceneConfig.getIndexDirectory()), Paths.get(luceneConfig.getTableDirectory()));
        System.err.println("\n-=========== FINE SEZIONE STATISTICHE ===========\n\n");
    }
}


package it.uniroma3.idd.config;

import lombok.Getter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class LuceneConfig {

    @Getter
    @Value("${lucene.queryExplain}")
    private boolean queryExplain;

    @Getter
    @Value("${lucene.index.directory}")
    private String indexDirectory;

    @Getter
    @Value("${lucene.index.initialize}")
    private boolean shouldInitializeIndex;

    @Getter
    @Value("${lucene.searcher.tresholdMultiplier}")
    private float treasholdMultiplier;

    @Getter
    @Value("${data.articles.path}")
    private String articlesPath;

    @Getter
    @Value("${data.tables.path}")
    private String tablesPath;

    @Getter
    @Value("${lucene.index_table.directory}")
    private String tableDirectory;

    @Bean
    public Analyzer customAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
                TokenStream filter = new PorterStemFilter(tokenizer);
                return new TokenStreamComponents(tokenizer, filter);
            }
        };
    }

    @Bean
    public Analyzer perFieldAnalyzer() {
        Map<String, Analyzer> perFieldAnalyzers = new HashMap<>();
        Analyzer simple = new SimpleAnalyzer();
        Analyzer standard = new StandardAnalyzer();
        Analyzer whitespace = new WhitespaceAnalyzer();

        // Articles
        perFieldAnalyzers.put("title", simple);
        perFieldAnalyzers.put("authors", simple);
        perFieldAnalyzers.put("paragraphs", standard);
        perFieldAnalyzers.put("articleAbstract", standard);

        return new PerFieldAnalyzerWrapper(customAnalyzer(), perFieldAnalyzers);
    }
}
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
    @Value("${lucene.index.initialize}")
    private boolean shouldInitializeIndex;

    @Getter
    @Value("${lucene.searcher.tresholdMultiplier}")
    private float treasholdMultiplier;

    // PATH INPUT
    @Getter
    @Value("${data.articles.path}")
    private String articlesPath;

    @Getter
    @Value("${data.tables.path}")
    private String tablesPath;

    @Getter
    @Value("${data.figures.path}")
    private String figuresPath;

    // PATH INDEX
    @Getter
    @Value("${lucene.index.directory}")
    private String indexDirectory;

    @Getter
    @Value("${lucene.index_table.directory}")
    private String tableDirectory;

    @Getter
    @Value("${lucene.index_figure.directory}")
    private String figureDirectory;


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

        // Tables
        perFieldAnalyzers.put("caption", simple);
        perFieldAnalyzers.put("body", whitespace);
        perFieldAnalyzers.put("mentions", standard);
        perFieldAnalyzers.put("context_paragraphs", standard);

        // Images
        perFieldAnalyzers.put("alt", simple);
        perFieldAnalyzers.put("src", simple);
        perFieldAnalyzers.put("mentions", standard);
        perFieldAnalyzers.put("context_paragraphs", standard);
        perFieldAnalyzers.put("saved_path", simple);
        perFieldAnalyzers.put("fileName", simple);

        return new PerFieldAnalyzerWrapper(customAnalyzer(), perFieldAnalyzers);
    }
}

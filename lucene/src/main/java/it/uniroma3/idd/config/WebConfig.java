package it.uniroma3.idd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mappa /raw_articles/** alla cartella papers/ nella root del workspace
        // Assumendo che l'applicazione giri nella cartella 'lucene', papers è in ../papers
        registry.addResourceHandler("/raw_articles/**")
                .addResourceLocations("file:../papers/");
    }
}

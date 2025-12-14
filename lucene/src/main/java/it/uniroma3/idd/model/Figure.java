package it.uniroma3.idd.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Figure {

    // ID univoco della figura (es. "2510.12175v3_Sx3.F1")
    private String id;

    // --- Dati estratti dal JSON ---

    // Nome/titolo del file sorgente
    @JsonProperty("source_file")
    private String sourceFilename;

    // URL completo dell'immagine
    @JsonProperty("image_url")
    private String imageUrl;

    // Caption della figura
    @JsonProperty("caption")
    private String caption;

    // Termini informativi estratti dalla caption
    @JsonProperty("informative_terms_identified")
    private List<String> informativeTerms;

    // Paragrafi che citano esplicitamente la figura
    @JsonProperty("citing_paragraphs")
    private List<String> citingParagraphs;

    // Paragrafi contestuali (con termini correlati)
    @JsonProperty("contextual_paragraphs")
    private List<ContextualParagraph> contextualParagraphs;

    /**
     * Costruttore completo per compatibilità con il Parser
     */
    public Figure(String id, String sourceFilename, String imageUrl, String caption,
                  List<String> informativeTerms, List<String> citingParagraphs,
                  List<ContextualParagraph> contextualParagraphs) {
        this.id = id;
        this.sourceFilename = sourceFilename;
        this.imageUrl = imageUrl;
        this.caption = caption;
        this.informativeTerms = informativeTerms;
        this.citingParagraphs = citingParagraphs;
        this.contextualParagraphs = contextualParagraphs;
    }

    /**
     * Metodo di utilità per ottenere tutto il testo ricercabile in un'unica stringa.
     */
    public String getAllSearchableText() {
        StringBuilder sb = new StringBuilder();
        if (caption != null) sb.append(caption).append(" ");
        if (informativeTerms != null) sb.append(String.join(" ", informativeTerms)).append(" ");
        
        if (citingParagraphs != null) {
            for (String p : citingParagraphs) {
                sb.append(p).append(" ");
            }
        }
        return sb.toString();
    }
}

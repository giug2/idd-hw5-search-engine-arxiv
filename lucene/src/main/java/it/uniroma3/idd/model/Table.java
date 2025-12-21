package it.uniroma3.idd.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor 
@JsonIgnoreProperties(ignoreUnknown = true)
public class Table {

    private String id; 

    // --- Dati JSON ---
    @JsonProperty("source_file")
    private String sourceFilename; 

    @JsonProperty("caption")
    private String caption;

    @JsonProperty("body")
    private String body; // Testo Pulito

    @JsonProperty("html_code")
    private String htmlCode; // HTML Grezzo

    @JsonProperty("citing_paragraphs")
    private List<String> citingParagraphs;

    @JsonProperty("contextual_paragraphs")
    private List<ContextualParagraph> contextualParagraphs;

    // --- RIMOSSO IL COSTRUTTORE MANUALE (Ci pensa Lombok) ---

    public String getAllSearchableText() {
        StringBuilder sb = new StringBuilder();
        if (caption != null) sb.append(caption).append(" ");
        if (body != null) sb.append(body).append(" "); 
        
        if (citingParagraphs != null) {
            for (String p : citingParagraphs) {
                sb.append(p).append(" "); 
            }
        }
        return sb.toString();
    }
}

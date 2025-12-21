package it.uniroma3.idd.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextualParagraph {
    
    // --- Modello utile per immagazzinare i paragrafi direttamente connessi alle tabelle e immagini ---

    @JsonProperty("html")
    private String html;

    @JsonProperty("matched_terms")
    private List<String> matchedTerms;
}

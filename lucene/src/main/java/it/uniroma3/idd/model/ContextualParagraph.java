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
/*modello utile per immagazzinare i paragrafi direttamente connessi alle tabelle*/

    // Lucene: Index as StoredField (for display) or maybe distinct TextField if you want to search inside context
    @JsonProperty("html")
    private String html;

    // Lucene: Index as TextField (tokenized) to match keywords
    @JsonProperty("matched_terms")
    private List<String> matchedTerms;
}
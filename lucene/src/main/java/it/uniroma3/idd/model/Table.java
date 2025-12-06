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
@JsonIgnoreProperties(ignoreUnknown = true) // Evita errori se aggiungi campi futuri nel JSON
public class Table {

    // --- Metadati (Non nel JSON, da popolare a runtime) ---
    
    // Lucene: StringField (Store.YES, Index.NOT_ANALYZED) - ID univoco (es. "S4.T1")
    private String id; 
    
    // Lucene: StringField - Utile per filtrare per file sorgente (es. "2509.16375v1")
    private String sourceFilename; 


    // --- Dati dal JSON ---

    // Lucene: TextField (Store.YES, Index.ANALYZED) - Fondamentale per la ricerca full-text
    @JsonProperty("caption")
    private String caption;

    // Lucene: StoredField (Store.YES) - Di solito non si indicizza l'HTML grezzo, lo si salva solo per visualizzarlo
    // Se vuoi cercarci dentro, ti consiglio di pulire i tag HTML prima di indicizzare.
    @JsonProperty("body")
    private String body;

    // Lucene: TextField (Store.NO/YES, Index.ANALYZED) - Ottimo per il boosting della rilevanza
    @JsonProperty("informative_terms_identified")
    private List<String> informativeTerms;

    // Lucene: TextField (Store.YES, Index.ANALYZED) - Il contesto principale della tabella
    @JsonProperty("citing_paragraphs")
    private List<String> citingParagraphs;

    // Struttura complessa
    @JsonProperty("contextual_paragraphs")
    private List<ContextualParagraph> contextualParagraphs;
    
    /**
     * Metodo di utilità per ottenere tutto il testo ricercabile in un'unica stringa.
     * Utile per creare un campo Lucene "content" generico.
     */
    public String getAllSearchableText() {
        StringBuilder sb = new StringBuilder();
        if (caption != null) sb.append(caption).append(" ");
        if (informativeTerms != null) sb.append(String.join(" ", informativeTerms)).append(" ");
        
        // Aggiungiamo il contenuto testuale dei paragrafi citanti (pulendo l'HTML se necessario)
        if (citingParagraphs != null) {
            for (String p : citingParagraphs) {
                 // Qui potresti usare Jsoup.parse(p).text() per pulire l'HTML
                sb.append(p).append(" "); 
            }
        }
        return sb.toString();
    }
}
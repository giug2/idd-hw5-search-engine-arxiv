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

    // --- Metadati (Calcolati a runtime, non presenti nel corpo dell'oggetto JSON) ---
    
    // Lucene: StringField (Store.YES, Index.NOT_ANALYZED) - ID univoco (es. "S4.T1")
    // Nota: Questo campo viene popolato dal Parser usando la chiave della mappa JSON
    private String id; 


    // --- Dati estratti direttamente dal JSON ---

    // Lucene: StringField - Utile per filtrare per file sorgente.
    // MODIFICA: Ora viene mappato direttamente dal campo "source_file" del JSON
    @JsonProperty("source_file")
    private String sourceFilename; 

    // Lucene: TextField (Store.YES, Index.ANALYZED) - Fondamentale per la ricerca full-text
    @JsonProperty("caption")
    private String caption;

    // Lucene: StoredField (Store.YES) - Di solito non si indicizza l'HTML grezzo, lo si salva solo per visualizzarlo
    @JsonProperty("body")
    private String body; // Contiene l'HTML grezzo

    // Lucene: TextField (Store.NO/YES, Index.ANALYZED) - Ottimo per il boosting della rilevanza
    @JsonProperty("informative_terms_identified")
    private List<String> informativeTerms;

    // Lucene: TextField (Store.YES, Index.ANALYZED) - Il contesto principale della tabella
    @JsonProperty("citing_paragraphs")
    private List<String> citingParagraphs;

    // Struttura complessa (Lista di oggetti o stringhe a seconda di come hai configurato il Parser)
    @JsonProperty("contextual_paragraphs")
    private List<ContextualParagraph> contextualParagraphs;
    
    // Campo ausiliario per il testo pulito (se non lo passi nel costruttore, puoi calcolarlo nel getter o nel service)
    private String bodyCleaned; 


    /* Costruttore manuale aggiornato per compatibilità con il Parser */
    public Table(String id, String sourceFilename, String caption, String body, String bodyCleaned, 
                 List<String> informativeTerms, List<String> citingParagraphs, 
                 List<ContextualParagraph> contextualParagraphs) {
        this.id = id;
        this.sourceFilename = sourceFilename;
        this.caption = caption;
        this.body = body;
        this.bodyCleaned = bodyCleaned;
        this.informativeTerms = informativeTerms;
        this.citingParagraphs = citingParagraphs;
        this.contextualParagraphs = contextualParagraphs;
    }

    /**
     * Metodo di utilità per ottenere tutto il testo ricercabile in un'unica stringa.
     * Utile per creare un campo Lucene "content" generico.
     */
    public String getAllSearchableText() {
        StringBuilder sb = new StringBuilder();
        if (caption != null) sb.append(caption).append(" ");
        if (informativeTerms != null) sb.append(String.join(" ", informativeTerms)).append(" ");
        
        // Aggiungiamo il contenuto testuale dei paragrafi citanti
        if (citingParagraphs != null) {
            for (String p : citingParagraphs) {
                 // Qui potresti usare Jsoup.parse(p).text() per pulire l'HTML se necessario
                sb.append(p).append(" "); 
            }
        }
        return sb.toString();
    }
}
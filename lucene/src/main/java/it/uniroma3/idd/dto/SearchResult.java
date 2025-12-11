package it.uniroma3.idd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String tipo; 
    private String idUnivoco; 
    private String titolo; 
    private String snippet; 
    private float score; 
    private String urlDettaglio; 

}

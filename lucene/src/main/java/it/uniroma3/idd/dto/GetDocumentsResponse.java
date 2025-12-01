package it.uniroma3.idd.dto;

import lombok.*;

import java.util.Collection;

@Data @NoArgsConstructor @AllArgsConstructor
public class GetDocumentsResponse {

    private Collection<GetDocumentResponse> documents;

}
package it.uniroma3.idd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private Map<String, List<SearchResult>> risultati;
    private Map<String, SearchMetrics> metrichePerIndice = new HashMap<>();
}

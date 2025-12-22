package it.uniroma3.idd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchMetrics {
    public String index;
    public double ndcg;
    public double rr;
    public double precision;
    public long time;
    public long totalHits;
}

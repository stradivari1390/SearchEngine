package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TotalStatistics {
    private int sites;
    private Long pages;
    private Long lemmas;
    private boolean indexing;
}
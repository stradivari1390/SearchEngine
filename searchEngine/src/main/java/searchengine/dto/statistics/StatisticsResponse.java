package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statisticsData;
}

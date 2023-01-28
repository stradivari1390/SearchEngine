package searchengine.dto.statistics;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StatisticsData {

    private TotalStatistics total;
    private List<DetailedStatisticsItem> detailed;
    public StatisticsData() {
        detailed = new ArrayList<>();
    }
    public void addDetailed(DetailedStatisticsItem site) {
        detailed.add(site);
    }
}
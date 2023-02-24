package searchengine.dto.responses;

import lombok.*;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import searchengine.dto.statistics.DetailedStatisticsItem;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class StatisticsResponse implements Response {
    private int totalSites;
    private int totalPages;
    private int totalLemmas;
    private List<DetailedStatisticsItem> detailedStatistics;

    @Override
    public String toString() {
        return "StatisticsResponse{" +
                "totalSites=" + totalSites +
                ", totalPages=" + totalPages +
                ", totalLemmas=" + totalLemmas +
                '}';
    }

    @SneakyThrows
    public JSONObject get() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("totalSites", totalSites);
        jsonObject.put("totalPages", totalPages);
        jsonObject.put("totalLemmas", totalLemmas);
        JSONArray jsonArray = new JSONArray();
        for (DetailedStatisticsItem item : detailedStatistics) {
            jsonArray.put(item.toJsonObject());
        }
        jsonObject.put("detailedStatistics", jsonArray);
        return jsonObject;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.OK;
    }
}
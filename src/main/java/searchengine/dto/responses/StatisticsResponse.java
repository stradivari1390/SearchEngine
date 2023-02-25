package searchengine.dto.responses;

import lombok.*;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.Statistics;
import searchengine.services.IndexingService;

@AllArgsConstructor
@Getter
@Setter
public class StatisticsResponse implements Response {
    private boolean result = true;
    private Statistics statistics;

    @Override
    public String toString() {
        return "StatisticsResponse{" +
                "totalSites=" + statistics.getTotal().getSites() +
                ", totalPages=" + statistics.getTotal().getPages() +
                ", totalLemmas=" + statistics.getTotal().getLemmas() +
                '}';
    }

    @SneakyThrows
    public JSONObject get() {
        JSONObject jsonObject = new JSONObject();
        JSONObject totalJsonObject = new JSONObject();
        totalJsonObject.put("sites", statistics.getTotal().getSites());
        totalJsonObject.put("pages", statistics.getTotal().getPages());
        totalJsonObject.put("lemmas", statistics.getTotal().getLemmas());
        totalJsonObject.put("indexing", IndexingService.isIndexing());
        jsonObject.put("result", true);
        jsonObject.put("statistics", new JSONObject().put("total", totalJsonObject).put("detailed", new JSONArray()));
        JSONArray detailedJsonArray = jsonObject.getJSONObject("statistics").getJSONArray("detailed");
        for (DetailedStatisticsItem item : statistics.getDetailed()) {
            detailedJsonArray.put(item.toJsonObject());
        }
        return jsonObject;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.OK;
    }
}
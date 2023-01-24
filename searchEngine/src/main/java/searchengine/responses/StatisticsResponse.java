package searchengine.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
public class StatisticsResponse extends Response {

    private boolean result;
    private StatisticsData statisticsData;
    private HttpStatus httpStatus;

    @Override
    public JSONObject get() {
            JSONObject response = new JSONObject();
            try {
                response.put("result", true);
                List<DetailedStatisticsItem> detailedStatistics = statisticsData.getDetailed();
                JSONArray detailedStatisticsArray = new JSONArray();
                for (DetailedStatisticsItem item : detailedStatistics) {
                    JSONObject itemJSON = new JSONObject();
                    itemJSON.put("url", item.getUrl());
                    itemJSON.put("name", item.getName());
                    itemJSON.put("status", item.getStatus());
                    itemJSON.put("statusTime", new Date(item.getStatusTime()));
                    itemJSON.put("error", item.getError());
                    itemJSON.put("pages", item.getPages());
                    itemJSON.put("lemmas", item.getLemmas());
                    detailedStatisticsArray.put(itemJSON);
                }
                response.put("statistics", detailedStatisticsArray);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return response;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
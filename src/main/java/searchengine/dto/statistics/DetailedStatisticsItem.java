package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.springframework.boot.configurationprocessor.json.JSONObject;

@Data
@AllArgsConstructor
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private Long statusTime;
    private String error;
    private Long pages;
    private Long lemmas;

    @SneakyThrows
    public JSONObject toJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", url);
        jsonObject.put("name", name);
        jsonObject.put("status", status);
        jsonObject.put("statusTime", statusTime);
        jsonObject.put("error", error);
        jsonObject.put("pages", pages);
        jsonObject.put("lemmas", lemmas);
        return jsonObject;
    }
}
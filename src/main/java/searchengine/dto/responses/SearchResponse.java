package searchengine.dto.responses;

import lombok.*;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import searchengine.dto.search.SearchResult;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SearchResponse implements Response {
    private boolean result;
    private int count;
    private List<SearchResult> data;

    @SneakyThrows
    @Override
    public JSONObject get() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("result", result);
        jsonObject.put("count", count);
        JSONArray jsonArray = new JSONArray();
        for (SearchResult searchResult : data) {
            jsonArray.put(searchResult.toJsonObject());
        }
        jsonObject.put("data", jsonArray);
        return jsonObject;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.OK;
    }
}
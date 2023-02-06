package searchengine.responses;

import lombok.AllArgsConstructor;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;



@AllArgsConstructor
public class SearchResponse implements Response {

    private JSONObject response;
    private HttpStatus httpStatus;

    @Override
    public JSONObject get() {
        return response;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
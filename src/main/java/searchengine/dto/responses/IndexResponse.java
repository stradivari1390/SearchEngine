package searchengine.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Data
public class IndexResponse implements Response {

    private boolean result;

    @SneakyThrows
    @Override
    public JSONObject get() {
        return new JSONObject().put("result", result);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.OK;
    }
}
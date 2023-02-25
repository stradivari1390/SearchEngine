package searchengine.dto.responses;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
public class ErrorResponse implements Response {
    private boolean result;
    private String error;

    @SneakyThrows
    @Override
    public JSONObject get() {
        return new JSONObject().put("result", result).put("error", error);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
package searchengine.responses;

import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;

public interface Response {
    JSONObject get();
    HttpStatus getHttpStatus();
}
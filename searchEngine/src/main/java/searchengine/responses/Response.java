package searchengine.responses;

import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;

public abstract class Response {
    public abstract JSONObject get();
    public abstract HttpStatus getHttpStatus();
}
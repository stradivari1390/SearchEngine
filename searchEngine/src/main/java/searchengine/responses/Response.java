package searchengine.responses;

import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.ResponseEntity;

public abstract class Response {
    public abstract ResponseEntity<JSONObject> get();
}
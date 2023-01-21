package searchengine.responses;

import lombok.Getter;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class IndexResponse extends Response {

    @Getter
    JSONObject response;

    public IndexResponse(String action, boolean b) {
        switch (action) {
            case "startIndexing": {
                try {
                    response = new JSONObject();
                    if (b) {
                        response.put("result", false);
                        response.put("error", "Индексация уже запущена");
                    } else {
                        response.put("result", true);
                    }
                    break;
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            case "stopIndexing": {
                try {
                    response = new JSONObject();
                    if (b) {
                        response.put("result", true);
                    } else {
                        response.put("result", false);
                        response.put("error", "Индексация не запущена");
                    }
                    break;
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            case "indexPage": {
                try {
                    response = new JSONObject();
                    if (b) {
                        response.put("result", true);
                    } else {
                        response.put("result", false);
                        response.put("error", "Данная страница находится за пределами сайтов, " +
                                "указанных в конфигурационном файле");
                    }
                    break;
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public ResponseEntity<JSONObject> get() {
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
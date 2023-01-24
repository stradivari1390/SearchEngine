package searchengine.responses;

import lombok.Getter;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;

public class IndexResponse extends Response {

    @Getter
    JSONObject response;
    private HttpStatus httpStatus;

    public IndexResponse(String action, boolean b) {
        switch (action) {
            case "startIndexing": {
                try {
                    response = new JSONObject();
                    if (b) {
                        response.put("result", false);
                        response.put("error", "Индексация уже запущена");
                        httpStatus = HttpStatus.BAD_REQUEST;
                    } else {
                        response.put("result", true);
                        httpStatus = HttpStatus.OK;
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
                        httpStatus = HttpStatus.OK;
                    } else {
                        response.put("result", false);
                        response.put("error", "Индексация не запущена");
                        httpStatus = HttpStatus.BAD_REQUEST;
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
                        httpStatus = HttpStatus.OK;
                    } else {
                        response.put("result", false);
                        response.put("error", "Данная страница находится за пределами сайтов, " +
                                "указанных в конфигурационном файле");
                        httpStatus = HttpStatus.BAD_REQUEST;
                    }
                    break;
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public JSONObject get() {
        return response;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
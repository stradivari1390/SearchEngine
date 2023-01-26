package searchengine.responses;

import lombok.Getter;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import searchengine.exceptions.IndexResponseException;

public class IndexResponse implements Response {

    private static final String RESULT = "result";
    private static final String ERROR = "error";

    @Getter
    JSONObject response;
    private final HttpStatus httpStatus;

    public IndexResponse(String action, boolean b) throws IndexResponseException {
        JSONObject tempResponse;
        HttpStatus tempHttpStatus;
        switch (action) {
            case "startIndexing":
                tempResponse = createStartIndexingResponse(b);
                tempHttpStatus = getHttpStatus(!b);
                break;
            case "stopIndexing":
                tempResponse = createStopIndexingResponse(b);
                tempHttpStatus = getHttpStatus(b);
                break;
            case "indexPage":
                tempResponse = createIndexPageResponse(b);
                tempHttpStatus = getHttpStatus(b);
                break;
            default:
                throw new IllegalArgumentException("action should be startIndexing, stopIndexing or indexPage");
        }
        this.response = tempResponse;
        this.httpStatus = tempHttpStatus;
    }

    private JSONObject createStartIndexingResponse(boolean b) throws IndexResponseException {
        try {
            JSONObject jsonObject = new JSONObject();
            if (b) {
                jsonObject.put(RESULT, false);
                jsonObject.put(ERROR, "Индексация уже запущена");
            } else {
                jsonObject.put(RESULT, true);
            }
            return jsonObject;
        } catch (JSONException e) {
            throw new IndexResponseException("startIndexing Error while creating JSONObject", e);
        }
    }

    private JSONObject createStopIndexingResponse(boolean b) throws IndexResponseException {
        try {
            JSONObject jsonObject = new JSONObject();
            if (b) {
                jsonObject.put(RESULT, true);
            } else {
                jsonObject.put(RESULT, false);
                jsonObject.put(ERROR, "Индексация не запущена");
            }
            return jsonObject;
        } catch (JSONException e) {
            throw new IndexResponseException("stopIndexing Error while creating JSONObject", e);
        }
    }

    private JSONObject createIndexPageResponse(boolean b) throws IndexResponseException {
        try {
            JSONObject jsonObject = new JSONObject();
            if (b) {
                jsonObject.put(RESULT, true);
            } else {
                jsonObject.put(RESULT, false);
                jsonObject.put(ERROR, "Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле");
            }
            return jsonObject;
        } catch (JSONException e) {
            throw new IndexResponseException("indexPage Error while creating JSONObject", e);
        }
    }

    private HttpStatus getHttpStatus(boolean b) {
        return b ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
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
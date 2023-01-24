package searchengine.responses;

import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;

public class ErrorResponse extends Response {

    JSONObject response;
    HttpStatus httpStatus;

    public ErrorResponse(JSONObject jsonObject, HttpStatus httpStatus) throws JSONException {
        response = jsonObject;
        this.httpStatus = httpStatus;
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
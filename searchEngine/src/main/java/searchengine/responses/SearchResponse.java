package searchengine.responses;

import lombok.Data;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import searchengine.dto.search.SearchResult;

import java.util.Set;
import java.util.TreeSet;

@Data
public class SearchResponse extends Response {

    private boolean result = true;

    private int count = 0;

    private Set<SearchResult> searchResultSet = new TreeSet<>();

    public void setSearchResultSet(Set<SearchResult> searchResultSet) {
        this.searchResultSet = searchResultSet;
        setCount(searchResultSet.size());
    }

    @Override
    public ResponseEntity<JSONObject> get() {

        JSONObject response = new JSONObject();
        try {
            response.put("result", result);

        response.put("count", count);

        JSONArray array = new JSONArray();

        for(SearchResult searchResult : searchResultSet) {
            JSONObject object = new JSONObject();

            object.put("site", searchResult.getSiteUrl());
            object.put("siteName", searchResult.getSiteName());
            object.put("uri", searchResult.getUri());
            object.put("title", searchResult.getTitle());
            object.put("snippet", searchResult.getSnippet());
            object.put("relevance", searchResult.getRelevance());

            array.put(object);
        }
        response.put("data", array);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
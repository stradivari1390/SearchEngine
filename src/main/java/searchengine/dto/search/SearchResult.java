package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.configurationprocessor.json.JSONObject;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SearchResult implements Comparable<SearchResult> {

    private String siteUrl;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    @Override
    public int compareTo(SearchResult o) {
        int relevanceComparison = Float.compare(o.relevance, relevance);
        if (relevanceComparison != 0) {
            return relevanceComparison;
        }
        int siteUrlComparison = siteUrl.compareTo(o.siteUrl);
        if (siteUrlComparison != 0) {
            return siteUrlComparison;
        }
        return title.compareTo(o.title);
    }

    @Override
    public String toString() {
        return "title: " + title + "\nuri: " + uri + "\nsnippet:\n" + snippet + "\nrel: " + relevance + "\n";
    }

    @SneakyThrows
    public JSONObject toJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("siteUrl", siteUrl);
        jsonObject.put("siteName", siteName);
        jsonObject.put("uri", uri);
        jsonObject.put("title", title);
        jsonObject.put("snippet", snippet);
        jsonObject.put("relevance", relevance);
        return jsonObject;
    }
}
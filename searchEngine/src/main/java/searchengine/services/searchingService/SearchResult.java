package searchengine.services.searchingService;

import lombok.Data;

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
        if (relevance > o.getRelevance()) {
            return -1;
        }
        return 1;
    }

    @Override
    public String toString() {
        return "title: " + title + "\nuri: " + uri + "\nsnippet:\n" + snippet + "\nrel: " + relevance + "\n";
    }
}
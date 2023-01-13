package searchengine.services.searchingService;

import lombok.Data;

import java.util.Set;
import java.util.TreeSet;

@Data
public class Search {

    private boolean result = true;

    private int count = 0;

    private Set<SearchResult> searchResultSet = new TreeSet<>();

    public void setSearchResultSet(Set<SearchResult> searchResultSet) {
        this.searchResultSet = searchResultSet;
        setCount(searchResultSet.size());
    }
}
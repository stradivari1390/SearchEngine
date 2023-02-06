package searchengine.dto.search;

import lombok.Data;

import java.util.Set;
import java.util.TreeSet;

@Data
public class Search {

    private boolean result;
    private int count;
    private Set<SearchResult> searchResultSet;

    public Search() {
        result = true;
        count = 0;
        searchResultSet = new TreeSet<>();
    }

    public void setSearchResultSet(Set<SearchResult> searchResultSet) {
        this.searchResultSet = searchResultSet;
        setCount(searchResultSet.size());
    }
}

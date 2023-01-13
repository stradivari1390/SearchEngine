package searchengine.services.searchingService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.repository.*;

import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class SearchingService {

    @Autowired
    private SiteRepository siteRepository;

    public Search search(String query, String site, int offset, int limit) {

        SearchEngine searchEngine = new SearchEngine();

        Search search = searchEngine.search(query, siteRepository.findByUrl(site));

        if (search.getCount() < offset) return new Search();

        if (search.getCount() > limit) {
            Set<SearchResult> searchResults = new TreeSet<>();

            search.getSearchResultSet().forEach(searchResult -> {
                if (searchResults.size() <= limit) {
                    searchResults.add(searchResult);
                }
            });
            search.setSearchResultSet(searchResults);
        }
        return search;
    }
}
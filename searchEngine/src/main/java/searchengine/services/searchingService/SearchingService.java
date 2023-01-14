package searchengine.services.searchingService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.repository.*;
import searchengine.services.lemmatisationService.Lemmatisator;

import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class SearchingService {

    @Autowired
    private Lemmatisator lemmatisator;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;

    public Search search(String query, String site, int offset, int limit) {

        SearchEngine searchEngine = new SearchEngine(siteRepository, pageRepository,
                lemmaRepository, indexRepository, lemmatisator);

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
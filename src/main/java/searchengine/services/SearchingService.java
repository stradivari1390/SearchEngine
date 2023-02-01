package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.exceptions.EmptyQueryException;
import searchengine.exceptions.NotFoundSiteException;
import searchengine.responses.ErrorResponse;
import searchengine.responses.Response;
import searchengine.responses.SearchResponse;
import searchengine.dto.search.SearchEngine;
import searchengine.dto.search.SearchResult;
import searchengine.repository.*;

import java.util.Set;
import java.util.TreeSet;

@Service
public class SearchingService {

    private IndexRepository indexRepository;
    private PageRepository pageRepository;
    private SiteRepository siteRepository;
    private LemmaRepository lemmaRepository;

    @Autowired
    public SearchingService(IndexRepository indexRepository, PageRepository pageRepository,
                            SiteRepository siteRepository, LemmaRepository lemmaRepository) {
        this.indexRepository = indexRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
    }

    public Response search(String query, String siteUrl, int offset, int limit) throws NotFoundSiteException {

        if (query == null || query.isEmpty()) {
            JSONObject response = new JSONObject();
            try {
                response.put("result", false);
                response.put("error", "Задан пустой поисковый запрос");
                return new ErrorResponse(response, HttpStatus.BAD_REQUEST);
            } catch (JSONException e) {
                throw new EmptyQueryException(e);
            }
        }

        if (siteUrl != null && siteRepository.findByUrl(siteUrl).getUrl() == null) {
            JSONObject response = new JSONObject();
            try {
                response.put("result", false);
                response.put("error", "Указанная страница не найдена");
                return new ErrorResponse(response, HttpStatus.NOT_FOUND);
            } catch (JSONException e) {
                throw new NotFoundSiteException(e);
            }
        }

        SearchEngine searchEngine = new SearchEngine(siteRepository, pageRepository,
                lemmaRepository, indexRepository);

        SearchResponse searchResponse = searchEngine.search(query, siteRepository.findByUrl(siteUrl));

        if (searchResponse.getCount() < offset) return new SearchResponse();

        if (searchResponse.getCount() > limit) {
            Set<SearchResult> searchResults = new TreeSet<>();

            searchResponse.getSearchResultSet().forEach(searchResult -> {
                if (searchResults.size() <= limit) {
                    searchResults.add(searchResult);
                }
            });
            searchResponse.setSearchResultSet(searchResults);
        }
        return searchResponse;
    }
}
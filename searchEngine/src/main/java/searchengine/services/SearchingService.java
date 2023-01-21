package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.responses.ErrorResponse;
import searchengine.responses.Response;
import searchengine.responses.SearchResponse;
import searchengine.dto.search.SearchEngine;
import searchengine.dto.search.SearchResult;
import searchengine.repository.*;
import searchengine.dto.Lemmatisator;

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

    public Response search(String query, String siteUrl, int offset, int limit) {

        if (query == null) {
            JSONObject response = new JSONObject();
            try {
                response.put("result", false);
                response.put("error", "Задан пустой поисковый запрос");
                return new ErrorResponse(response, HttpStatus.BAD_REQUEST);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        if (siteUrl != null && siteRepository.findByUrl(siteUrl).getUrl() == null) {
            JSONObject response = new JSONObject();
            try {
                response.put("result", false);
                response.put("error", "Указанная страница не найдена");
                return new ErrorResponse(response, HttpStatus.NOT_FOUND);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        SearchEngine searchEngine = new SearchEngine(siteRepository, pageRepository,
                lemmaRepository, indexRepository, lemmatisator);

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
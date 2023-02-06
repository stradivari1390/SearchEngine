package searchengine.services;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.dto.search.Search;
import searchengine.exceptions.EmptyQueryException;
import searchengine.exceptions.NotFoundSiteException;
import searchengine.exceptions.SearchResultException;
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
    private static final String RESULT = "result";
    private static final String ERROR = "error";
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;

    @Autowired
    public SearchingService(IndexRepository indexRepository, PageRepository pageRepository,
                            SiteRepository siteRepository, LemmaRepository lemmaRepository) {
        this.indexRepository = indexRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
    }

    @SneakyThrows
    public Response search(String query, String siteUrl, int offset, int limit) {
        if (query == null || query.isEmpty()) {
            return createEmptyQueryErrorResponse();
        }
        if (siteUrl != null && siteRepository.findByUrl(siteUrl).getUrl() == null) {
            return createNotFoundSiteErrorResponse();
        }

        SearchEngine searchEngine = new SearchEngine(siteRepository, pageRepository,
                lemmaRepository, indexRepository);

        Search search = searchEngine.search(query, siteRepository.findByUrl(siteUrl));

        if (search.getCount() < offset) {
            return createSearchResultErrorResponse();
        }
        if (search.getCount() > limit) {
            search.setSearchResultSet(getLimitSearchResultSet(search, limit));
        }
        return createSearchResponse(search);
    }

    private Response createEmptyQueryErrorResponse() {
        JSONObject response = new JSONObject();
        try {
            response.put(RESULT, false);
            response.put(ERROR, "Задан пустой поисковый запрос");
            return new ErrorResponse(response, HttpStatus.BAD_REQUEST);
        } catch (JSONException e) {
            throw new EmptyQueryException(e);
        }
    }

    @SneakyThrows
    private Response createNotFoundSiteErrorResponse() {
        JSONObject response = new JSONObject();
        try {
            response.put(RESULT, false);
            response.put(ERROR, "Указанная страница не найдена");
            return new ErrorResponse(response, HttpStatus.NOT_FOUND);
        } catch (JSONException e) {
            throw new NotFoundSiteException(e);
        }
    }

    @SneakyThrows
    private Response createSearchResultErrorResponse() {
        JSONObject response = new JSONObject();
        try {
            response.put(RESULT, false);
        } catch (JSONException e) {
            throw new SearchResultException(e);
        }
        return new SearchResponse(response, HttpStatus.BAD_REQUEST);
    }

    private Set<SearchResult> getLimitSearchResultSet(Search search, int limit) {
        Set<SearchResult> searchResults = new TreeSet<>();
        search.getSearchResultSet().forEach(searchResult -> {
            if (searchResults.size() <= limit) {
                searchResults.add(searchResult);
            }
        });
        return searchResults;
    }

    @SneakyThrows
    private Response createSearchResponse(Search search) {
        JSONObject response = new JSONObject();
        try {
            response.put(RESULT, search.isResult());
            response.put("count", search.getCount());

            JSONArray array = new JSONArray();

            for (SearchResult searchResult : search.getSearchResultSet()) {
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
            throw new SearchResultException(e);
        }
        return new SearchResponse(response, HttpStatus.OK);
    }
}
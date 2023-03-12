package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import searchengine.dto.responses.Response;
import searchengine.dto.search.Search;
import searchengine.dto.responses.ErrorResponse;
import searchengine.dto.responses.SearchResponse;
import searchengine.services.searching.SearchEngine;
import searchengine.dto.search.SearchResult;
import searchengine.model.Site;
import searchengine.repository.*;


import java.util.ArrayList;
import java.util.List;

@Service
public class SearchingService {

    private final SiteRepository siteRepository;
    private final SearchEngine searchEngine;

    @Autowired
    public SearchingService(SiteRepository siteRepository, SearchEngine searchEngine) {
        this.siteRepository = siteRepository;
        this.searchEngine = searchEngine;
    }

    public Response search(String query, String siteUrl, int offset, int limit) {
        if (query == null || query.isEmpty()) {
            return new ErrorResponse(false, "Задан пустой поисковый запрос");
        }
        Site site = null;
        if (siteUrl != null) {
            site = siteRepository.findSiteByUrl(siteUrl);
            if (site == null) {
                return new ErrorResponse(false, "Указанная страница не найдена");
            }
        }

        Search search = searchEngine.search(query, site, offset, limit);

        if (search.getCount() < offset) {
            return new ErrorResponse(false, "Некорректное значение смещения");
        }
        List<SearchResult> resultList = new ArrayList<>(search.getSearchResultSet());

        resultList = resultList.subList(offset, Math.min(offset + limit, search.getCount()));

        return new SearchResponse(search.isResult(), search.getCount(), resultList);
    }
}
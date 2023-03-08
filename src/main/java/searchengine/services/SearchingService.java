package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import searchengine.dto.responses.Response;
import searchengine.dto.search.Search;
import searchengine.dto.responses.ErrorResponse;
import searchengine.dto.responses.SearchResponse;
import searchengine.dto.search.SearchEngine;
import searchengine.dto.search.SearchResult;
import searchengine.model.Site;
import searchengine.repository.*;
import searchengine.services.parsing.Lemmatisator;

import java.util.ArrayList;
import java.util.List;

@Service
public class SearchingService {
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final Lemmatisator lemmatisator;

    @Autowired
    public SearchingService(IndexRepository indexRepository, PageRepository pageRepository,
                            SiteRepository siteRepository, LemmaRepository lemmaRepository,
                            Lemmatisator lemmatisator) {
        this.indexRepository = indexRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.lemmatisator = lemmatisator;
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
        SearchEngine searchEngine = new SearchEngine(siteRepository, pageRepository,
                lemmaRepository, indexRepository, lemmatisator);
        Search search = searchEngine.search(query, site);

        if (search.getCount() < offset) {
            return new ErrorResponse(false, "Некорректное значение смещения");
        }
        List<SearchResult> resultList = new ArrayList<>(search.getSearchResultSet());
        if (offset + limit < search.getCount()) {
            resultList = resultList.subList(offset, offset + limit);
        } else {
            resultList = resultList.subList(offset, search.getCount());
        }
        return new SearchResponse(search.isResult(), search.getCount(), resultList);
    }
}
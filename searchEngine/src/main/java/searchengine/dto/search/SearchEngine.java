package searchengine.dto.search;

import lombok.Data;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.dto.Lemmatisator;
import searchengine.responses.SearchResponse;

import java.util.*;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Data
public final class SearchEngine {

    private final Lemmatisator lemmatisator;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;

    public SearchEngine(SiteRepository siteRepository, PageRepository pageRepository,
                        LemmaRepository lemmaRepository, IndexRepository indexRepository,
                        Lemmatisator lemmatisator) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmatisator = lemmatisator;
    }

    public SearchResponse search(String query, Site site) {
        Set<SearchResult> searchResults;
        if (site == null) {
            searchResults = new TreeSet<>();
            siteRepository.findAll().forEach(s -> searchResults.addAll(getSearchesBySite(s, query)));
        } else {
            searchResults = getSearchesBySite(site, query);
        }
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setCount(searchResults.size());
        searchResponse.setResult(true);
        searchResponse.setSearchResultSet(searchResults);
        return searchResponse;
    }

    private Set<SearchResult> getSearchesBySite(Site site, String query) {
        List<Page> pageList = pageRepository.findAllBySite(site);
        return addSearchQuery(site, query, pageList);
    }

    private Set<SearchResult> addSearchQuery(Site site, String query, List<Page> pageList) {
        Map<String, Integer> lemmas = lemmatisator.collectLemmasAndRanks(query);
        Set<Lemma> lemmaSet = new HashSet<>();
        lemmas.keySet().forEach(lemma -> lemmaSet.add(lemmaRepository.findLemmaByLemmaAndSite(lemma, site)));
        List<IndexRank> indexRanks = getIndexRanks(lemmaSet, pageList);
        return getSearchResults(indexRanks, lemmaSet, site);
    }

    private List<IndexRank> getIndexRanks(Set<Lemma> lemmaSet, List<Page> pageList) {
        List<IndexRank> indexRankList = new ArrayList<>();
        pageList.forEach(page -> {
            Map<String, Float> pageLemmas = new HashMap<>();
            lemmaSet.forEach(lemma -> {
                Index index = indexRepository.findByLemmaAndPage(lemma, page);
                if (index != null) {
                    pageLemmas.put(lemma.getLemma(), index.getRank());
                }
            });
            if (!pageLemmas.isEmpty()) {
                IndexRank indexRank = new IndexRank();
                indexRank.setPage(page);
                indexRank.setRankMap(pageLemmas);
                indexRank.setrAbs();
                indexRank.setrRel();
                indexRankList.add(indexRank);
            }
        });
        return indexRankList;
    }

    private SortedSet<SearchResult> getSearchResults(List<IndexRank> indexRanks, Set<Lemma> lemmaSet, Site site) {
        SortedSet<SearchResult> searchResults = new TreeSet<>();
        indexRanks.forEach(indexRank -> {
            Document document = Jsoup.parse(indexRank.getPage().getContent());
            String text = document.text().toLowerCase();
            int count = 0;
            String snippet = "";
            Set<Lemma> lemmaToRemove = new HashSet<>();
            for (Lemma lemma : lemmaSet) {
                String lemmaString = lemma.getLemma();
                if (text.contains(lemmaString)) {
                    count++;
                    text = text.replaceAll("(?i)" + lemmaString, "<b>" + lemmaString + "</b>");
                    int start = text.indexOf("<b>") - 50;
                    int end = text.indexOf("</b>") + 50;
                    if (start < 0) start = 0;
                    if (end > text.length()) end = text.length() - 1;
                    snippet = "..." + text.substring(start, end) + "...";
                } else lemmaToRemove.add(lemma);
            }
            lemmaSet.removeAll(lemmaToRemove);
            if (count > 0) {
                SearchResult searchResult = new SearchResult();
                searchResult.setTitle(document.title());
                searchResult.setRelevance(indexRank.getRRel());
                searchResult.setSnippet(snippet);
                searchResult.setUri(Objects.equals(indexRank.getPage().getPath(), site.getUrl()) ?
                        site.getUrl() :
                        indexRank.getPage().getPath().replace(site.getUrl(), ""));
                searchResult.setSiteUrl(site.getUrl());
                searchResult.setSiteName(site.getName());
                searchResults.add(searchResult);
            }
        });
        return searchResults;
    }
}
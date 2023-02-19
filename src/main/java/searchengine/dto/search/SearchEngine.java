package searchengine.dto.search;

import lombok.Data;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.parsing.Lemmatisator;

import java.util.*;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Data
public final class SearchEngine {

    private Lemmatisator lemmatisator;
    private IndexRepository indexRepository;
    private PageRepository pageRepository;
    private SiteRepository siteRepository;
    private LemmaRepository lemmaRepository;

    @Autowired
    public SearchEngine(SiteRepository siteRepository, PageRepository pageRepository,
                        LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    public Search search(String query, Site site) {
        Set<SearchResult> searchResults;
        if (site == null) {
            searchResults = new TreeSet<>();
            siteRepository.findAll().forEach(s -> searchResults.addAll(getSearchesBySite(query, s)));
        } else {
            searchResults = getSearchesBySite(query, site);
        }
        Search search = new Search();
        search.setCount(searchResults.size());
        search.setResult(true);
        search.setSearchResultSet(searchResults);
        return search;
    }

    @SneakyThrows
    private Set<SearchResult> getSearchesBySite(String query, Site site) {
        List<Page> pageList = pageRepository.findAllBySite(site);
        return addSearchQuery(site, query, pageList);
    }

    @SneakyThrows
    private Set<SearchResult> addSearchQuery(Site site, String query, List<Page> pageList) {
        lemmatisator = new Lemmatisator();
        Map<String, Integer> lemmas = lemmatisator.collectLemmasAndRanks(query);
        Set<Lemma> lemmaSet = new HashSet<>();
        lemmas.keySet().forEach(lemma -> lemmaSet.add(lemmaRepository.findLemmaByLemmaStringAndSite(lemma, site)));
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
                    pageLemmas.put(lemma.getLemmaString(), index.getRank());
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
            String snippet = createSnippet(text, lemmaSet);
            if (snippet.length() > 0) {
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

    private String createSnippet(String text, Set<Lemma> lemmaSet) {
        String snippet = "";
        Set<Lemma> lemmaToRemove = new HashSet<>();
        for (Lemma lemma : lemmaSet) {
            String lemmaString = lemma.getLemmaString();
            if (text.contains(lemmaString)) {
                text = text.replaceAll("(?i)" + lemmaString, "<b>" + lemmaString + "</b>");
                int start = text.indexOf("<b>") - 50;
                int end = text.indexOf("</b>") + 50;
                if (start < 0) start = 0;
                if (end >= text.length()) end = text.length() - 1;
                snippet = "..." + text.substring(start, end) + "...";
            } else lemmaToRemove.add(lemma);
        }
        lemmaSet.removeAll(lemmaToRemove);
        return snippet;
    }
}
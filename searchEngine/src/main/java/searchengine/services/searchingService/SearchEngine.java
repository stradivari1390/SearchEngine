package searchengine.services.searchingService;

import lombok.Data;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.lemmatisationService.Lemmatisator;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Data
public class SearchEngine {

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

    public SearchEngine() {}

    public Search search(String query, Site site) {

        SortedSet<SearchResult> searchResults = new TreeSet<>();

        if (site == null) {
            siteRepository.findAll().forEach(s -> searchResults.addAll(getSearchesBySite(s, query)));
        } else {
            searchResults.addAll(getSearchesBySite(site, query));
        }

        Search search = new Search();

        search.setCount(searchResults.size());
        search.setResult(true);
        search.setSearchResultSet(searchResults);

        return search;
    }

    private Set<SearchResult> getSearchesBySite(Site site, String query) {
        System.out.println(site.getUrl());
        List<Page> pageList = pageRepository.findAllBySite(site);
        return addSearchQuery(site, query, pageList);
    }

    private Set<SearchResult> addSearchQuery(Site site, String query, List<Page> pageList) {

        Map<String, Integer> lemmas = lemmatisator.collectLemmasAndRanks(query);

        Set<Lemma> lemmaSet = new HashSet<>();

        for(String lemma : lemmas.keySet()) {
            lemmaSet.add(lemmaRepository.findLemmaByLemmaAndSite(lemma, site));
        }

        List<IndexRank> indexRanks = getIndexRanks(lemmaSet, pageList);

        return getSearchResults(indexRanks, lemmaSet, site);
    }

    private List<IndexRank> getIndexRanks(Set<Lemma> lemmaSet, List<Page> pageList) {

        List<IndexRank> indexRankList = new ArrayList<>();

        lemmaSet.forEach(lemma -> {

            for (Page page : pageList) {

                Index index = indexRepository.findByLemmaAndPage(lemma, page);
                if (index != null) {

                    IndexRank indexRank = new IndexRank();

                    indexRank.setPage(page);
                    indexRank.addRank(lemma.getLemma(), index.getRank());
                    indexRank.setrAbs();

                    indexRankList.add(indexRank);
                }
            }
        });
        indexRankList.forEach(IndexRank::setrRel);
        return indexRankList;
    }

    private SortedSet<SearchResult> getSearchResults(List<IndexRank> indexRanks, Set<Lemma> lemmaSet, Site site) {

        SortedSet<SearchResult> searchResults = new TreeSet<>();

        indexRanks.forEach(indexRank -> {

            Document document = Jsoup.parse(indexRank.getPage().getContent());

            AtomicReference<String> snippet = new AtomicReference<>("");
            AtomicInteger maxSnippet = new AtomicInteger();
            SearchResult searchResult = new SearchResult();
            AtomicBoolean Done = new AtomicBoolean();

            document.getAllElements().forEach(pageElement -> {

                String text = pageElement.text().toLowerCase();
                int count = 0;
                for (Lemma lemma : lemmaSet) {
                    String lemmaString = lemma.getLemma();
                    if (text.contains(lemmaString)) {
                        count++;
                        text = text.replaceAll("(?i)" + lemmaString,
                                "<b>" + lemmaString + "</b>");
                    } else {
                        lemmaSet.remove(lemma);
                    }
                }

                if (count > maxSnippet.get()) {
                    snippet.set(text);
                    maxSnippet.set(count);
                    Done.set(true);
                }
            });

            if (Done.get()) {
                searchResult.setTitle(document.title());
                searchResult.setRelevance(indexRank.getRRel());
                searchResult.setSnippet(snippet.get());
                searchResult.setUri(indexRank.getPage().getPath().replace(site.getUrl(), ""));
                searchResult.setSiteUrl(site.getUrl());
                searchResult.setSiteName(site.getName());

                searchResults.add(searchResult);
            }
        });
        return searchResults;
    }
}
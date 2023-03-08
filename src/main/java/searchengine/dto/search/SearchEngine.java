package searchengine.dto.search;

import lombok.SneakyThrows;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.parsing.Lemmatisator;

import java.util.*;
import java.util.stream.Collectors;

@Component
public final class SearchEngine {

    private final Lemmatisator lemmatisator;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private static final HtmlCleaner cleaner = new HtmlCleaner();

    @Autowired
    public SearchEngine(SiteRepository siteRepository, PageRepository pageRepository,
                        LemmaRepository lemmaRepository, IndexRepository indexRepository,
                        Lemmatisator lemmatisator) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmatisator = lemmatisator;
    }

    public Search search(String query, Site site) {
        Set<SearchResult> searchResults;
        if (site == null) {
            searchResults = new TreeSet<>();
            siteRepository.findAll().forEach(s -> searchResults.addAll(getSearchesBySite(query, s)));
        } else {
            searchResults = new TreeSet<>(getSearchesBySite(query, site));
        }
        Search search = new Search();
        search.setCount(searchResults.size());
        search.setResult(true);
        search.setSearchResultSet(searchResults);
        return search;
    }

    @SneakyThrows
    private List<SearchResult> getSearchesBySite(String query, Site site) {
        List<Page> pageList = pageRepository.findAllBySite(site);
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

    private List<SearchResult> getSearchResults(List<IndexRank> indexRanks, Set<Lemma> lemmaSet, Site site) {
        return indexRanks.parallelStream()
                .map(indexRank -> createSearchResult(indexRank, lemmaSet, site))
                .filter(searchResult -> searchResult.getSnippet().length() > 0)
                .collect(Collectors.toList());
    }

    private SearchResult createSearchResult(IndexRank indexRank, Set<Lemma> lemmaSet, Site site) {
        String html = indexRank.getPage().getContent();
        TagNode node = cleaner.clean(html);
        String text = node.getText().toString().toLowerCase();
        String snippet = createSnippet(text, lemmaSet);
        SearchResult searchResult = new SearchResult();
        setSearchResultTitle(node, text, searchResult);
        searchResult.setRelevance(indexRank.getRRel());
        searchResult.setSnippet(snippet);
        searchResult.setUri(Objects.equals(indexRank.getPage().getPath(), site.getUrl()) ?
                site.getUrl() :
                indexRank.getPage().getPath().replace(site.getUrl(), ""));
        searchResult.setSiteUrl(site.getUrl());
        searchResult.setSiteName(site.getName());
        return searchResult;
    }

    private void setSearchResultTitle(TagNode node, String text, SearchResult searchResult) {
        try {
            String title = getTitleFromNode(node);
            if (title == null) {
                title = getTitleFromAttribute(node);
            }
            if (title == null) {
                title = getTitleFromLemmas(text);
            }
            searchResult.setTitle(title);
        } catch (XPatherException e) {
            e.printStackTrace();
        }
    }

    private String getTitleFromNode(TagNode node) throws XPatherException {
        Object[] titleNodes = node.evaluateXPath("//head/title");
        if (titleNodes.length > 0) {
            TagNode titleNode = (TagNode) titleNodes[0];
            return titleNode.getText().toString();
        }
        return null;
    }

    private String getTitleFromAttribute(TagNode node) throws XPatherException {
        Object[] titleNodes = node.evaluateXPath("//span[@title]");
        for (Object obj : titleNodes) {
            if (obj instanceof TagNode) {
                TagNode spanNode = (TagNode) obj;
                return spanNode.getAttributeByName("title");
            }
        }
        return null;
    }

    private String getTitleFromLemmas(String text) {
        Map<String, Integer> lemmas = lemmatisator.collectLemmasAndRanks(text);
        Map.Entry<String, Integer> maxEntry = null;
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                maxEntry = entry;
            }
        }
        return maxEntry == null ? "" :
                maxEntry.getKey().substring(0, 1).toUpperCase() + maxEntry.getKey().substring(1);
    }

    private String createSnippet(String text, Set<Lemma> lemmaSet) {
        String snippet = "";
        Set<Lemma> lemmaToRemove = new HashSet<>();
        for (Lemma lemma : lemmaSet) {
            String lemmaString = lemma.getLemmaString();
            if (text.contains(lemmaString)) {
                text = text.replaceAll("(?i)" + lemmaString, "<b>" + lemmaString + "</b>");
                int start = text.indexOf("<b>") - 75;
                int end = text.indexOf("</b>") + 75;
                if (start < 0) start = 0;
                if (end >= text.length()) end = text.length() - 1;
                snippet = "..." + text.substring(start, end) + "...";
            } else lemmaToRemove.add(lemma);
        }
        lemmaSet.removeAll(lemmaToRemove);
        return snippet;
    }
}
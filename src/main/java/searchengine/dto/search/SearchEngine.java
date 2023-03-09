package searchengine.dto.search;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.parsing.Lemmatisator;

import java.util.*;

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

    public List<SearchResult> getResultList(String query, Site site) {
        List<SearchResult> results = new ArrayList<>();
        int limitOfPresence = (int) (pageRepository.countBySite(site) * 0.8);
        Map<String, Integer> lemmas = lemmatisator.collectLemmasAndRanks(query);
        List<Lemma> uniqueLemmas = findUniqueLemmas(lemmas, site, limitOfPresence);
        if (uniqueLemmas.isEmpty()) {
            return results;
        }
        List<Page> matchingPages = findMatchingPages(uniqueLemmas);
        if (matchingPages.isEmpty()) {
            return results;
        }
        Map<Page, Float> pageRelevanceMap = getPageRelevanceMap(matchingPages, uniqueLemmas);
        float maxRelevance = getMaxRelevance(pageRelevanceMap);
        for (Map.Entry<Page, Float> entry : pageRelevanceMap.entrySet()) {
            Page page = entry.getKey();
            Document doc = Jsoup.parse(page.getContent());
            String text = doc.text();
            float relativeRelevance = maxRelevance != 0 ? entry.getValue() / maxRelevance : 0;
            results.add(new SearchResult(site.getUrl(),
                    site.getName(),
                    Objects.equals(page.getPath(), site.getUrl()) ?
                            site.getUrl() :
                            page.getPath().replace(site.getUrl(), ""),
                    getSearchResultTitle(page, text),
                    generateSnippet(text, uniqueLemmas),
                    relativeRelevance));
        }
        return results;
    }

    private List<Lemma> findUniqueLemmas(Map<String, Integer> lemmas, Site site, int limitOfPresence) {
        List<Lemma> uniqueLemmas = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            Lemma lemmaFromRepository = lemmaRepository.findLemmaByLemmaStringAndSite(entry.getKey(), site);
            if (lemmaFromRepository != null && lemmaFromRepository.getFrequency() <= limitOfPresence) {
                uniqueLemmas.add(lemmaFromRepository);
            } else {
                return new ArrayList<>();
            }
        }
        uniqueLemmas.sort(Comparator.comparingInt(Lemma::getFrequency));
        return uniqueLemmas;
    }

    private List<Page> findMatchingPages(List<Lemma> uniqueLemmas) {
        List<Page> matchingPages = new ArrayList<>();
        for (Lemma lemma : uniqueLemmas) {
            List<Index> indices = indexRepository.findAllByLemma(lemma);
            if (indices == null || indices.isEmpty()) {
                matchingPages.clear();
                break;
            }
            List<Page> pages = pageRepository.findAllByIndices(indices);
            if (matchingPages.isEmpty()) {
                matchingPages.addAll(pages);
            } else {
                matchingPages.retainAll(pages);
                if (matchingPages.isEmpty()) {
                    break;
                }
            }
        }
        return matchingPages;
    }

    private Map<Page, Float> getPageRelevanceMap(List<Page> matchingPages, List<Lemma> uniqueLemmas) {
        Map<Page, Float> pageRelevanceMap = new HashMap<>();
        for (Page page : matchingPages) {
            float absoluteRelevance = 0;
            for (Lemma lemma : uniqueLemmas) {
                Index index = indexRepository.findByLemmaAndPage(lemma, page);
                if (index != null) {
                    absoluteRelevance += index.getRank();
                }
            }
            pageRelevanceMap.put(page, absoluteRelevance);
        }
        return pageRelevanceMap;
    }

    private float getMaxRelevance(Map<Page, Float> pageRelevanceMap) {
        return pageRelevanceMap.values().stream()
                .max(Float::compareTo)
                .orElse(0f);
    }

    public Search search(String query, Site site) {
        Set<SearchResult> searchResults;
        if (site == null) {
            searchResults = new TreeSet<>();
            siteRepository.findAll().forEach(s -> searchResults.addAll(getResultList(query, s)));
        } else {
            searchResults = new TreeSet<>(getResultList(query, site));
        }
        Search search = new Search();
        search.setCount(searchResults.size());
        search.setResult(true);
        search.setSearchResultSet(searchResults);
        return search;
    }

    private String getSearchResultTitle(Page page, String text) {
        TagNode node = cleaner.clean(page.getContent());
        String title = "";
        try {
            title = getTitleFromNode(node);
            if (title == null) {
                title = getTitleFromAttribute(node);
            }
            if (title == null) {
                title = getTitleFromLemmas(text);
            }
        } catch (XPatherException e) {
            e.printStackTrace();
        }
        return title;
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

    private String generateSnippet(String text, List<Lemma> lemmas) {
        StringBuilder snippetBuilder = new StringBuilder();
        for (Lemma lemma : lemmas) {
            String lemmaString = lemma.getLemmaString().toLowerCase();
            int index = text.toLowerCase().indexOf(lemmaString);
            if (index != -1) {
                int end = Math.min(text.length(), index + lemmaString.length() + 80);
                int startIndex = Math.max(0, index - 80);
                String snippet = text.substring(startIndex, end);
                int lemmaStart = snippet.toLowerCase().indexOf(lemmaString);
                int lemmaEnd = lemmaStart + lemmaString.length();
                snippet = snippet.substring(0, lemmaStart) + "<b>" + snippet.substring(lemmaStart, lemmaEnd) + "</b>" + snippet.substring(lemmaEnd);
                if (startIndex > 0) {
                    snippet = " ... " + snippet;
                }
                if (end < text.length()) {
                    snippet = snippet + " ... ";
                }
                snippetBuilder.append(snippet).append(" ");
            }
        }
        return snippetBuilder.toString();
    }
}
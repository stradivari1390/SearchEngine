package searchengine.services.searching;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.dto.search.Search;
import searchengine.dto.search.SearchResult;
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
        int limitOfPresence = (int) (pageRepository.countBySiteAndCode(site, 200) * 0.8);
        List<String> lemmas = lemmatisator.convertTextIntoLemmasList(query);
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
            float relativeRelevance = maxRelevance != 0 ? entry.getValue() / maxRelevance : 0;
            results.add(new SearchResult(site.getUrl(),
                    site.getName(),
                    Objects.equals(page.getPath(), site.getUrl()) ?
                            site.getUrl() :
                            page.getPath().replace(site.getUrl(), ""),
                    page.getPath(),
                    page.getPath(),
                    relativeRelevance));
        }
        return results;
    }

    private List<Lemma> findUniqueLemmas(List<String> lemmas, Site site, int limitOfPresence) {
        List<Lemma> uniqueLemmas = new ArrayList<>();
        for (String lemma : lemmas) {
            Lemma lemmaFromRepository = lemmaRepository.findLemmaByLemmaStringAndSite(lemma, site);
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
        long lemmaCount = uniqueLemmas.size();
        return pageRepository.findAllByLemmas(uniqueLemmas, lemmaCount);
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

    public Search search(String query, Site site, int offset, int limit) {
        TreeSet<SearchResult> searchResults;
        if (site == null) {
            searchResults = new TreeSet<>();
            siteRepository.findAll().forEach(s -> searchResults.addAll(getResultList(query, s)));
        } else {
            searchResults = new TreeSet<>(getResultList(query, site));
        }

        List<SearchResult> resultList = new ArrayList<>(searchResults);
        int endIndex = Math.min(offset + limit, resultList.size());
        List<SearchResult> subList = resultList.subList(offset, endIndex);
        subList.forEach(s -> {
            Page page = pageRepository.findByPath(s.getSnippet());
            Document doc = Jsoup.parse(page.getContent());
            String text = doc.text();
            List<String> lemmas = lemmatisator.convertTextIntoLemmasList(query);
            s.setSnippet(generateHighlightedSnippet(text, lemmas));
            s.setTitle(getSearchResultTitle(page, text));
        });

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

    private String generateHighlightedSnippet(String text, List<String> lemmas) {
        StringBuilder result = new StringBuilder();
        String[] words = text.split("[^a-zA-Zа-яА-Я]+");
        StringBuilder highlightedTextBuilder = new StringBuilder(text);
        int currentIndex = 0;
        for (String word : words) {
            String lemma = lemmatisator.getWordLemma(word.toLowerCase());
            if (lemmas.contains(lemma)) {
                int startIndex = highlightedTextBuilder.indexOf(word, currentIndex);
                int endIndex = startIndex + word.length();
                highlightedTextBuilder.insert(startIndex, "<b>");
                highlightedTextBuilder.insert(endIndex + 3, "</b>");
                currentIndex = endIndex + 7;
            }
        }
        result.append(extractSnippetWithMaxHighlights(highlightedTextBuilder));
        return result.toString();
    }

    private StringBuilder extractSnippetWithMaxHighlights(StringBuilder inputText) {
        List<Integer> boldWordsIndices = findSubstringIndices(inputText, "<b>");
        int[] firstAndLastIndices = findMaxConsecutiveSubsequence(boldWordsIndices);
        int maxIndex = Math.min(
                firstAndLastIndices[1] + (firstAndLastIndices[0] == firstAndLastIndices[1] ? 100 : 50),
                inputText.length()
        );
        int minIndex = Math.max(
                0,
                firstAndLastIndices[0] - (firstAndLastIndices[0] == firstAndLastIndices[1] ? 100 : 50)
        );
        int initialLength = inputText.length();
        inputText.delete(maxIndex, inputText.length());
        if (maxIndex < initialLength) {
            inputText.delete(inputText.lastIndexOf(" "), inputText.length());
            inputText.append("...");
        }
        inputText.delete(0, minIndex);
        if (minIndex > 0) {
            inputText.delete(0, inputText.indexOf(" "));
            inputText.insert(0, "...");
        }
        return inputText;
    }

    private List<Integer> findSubstringIndices(StringBuilder text, String substring) {
        List<Integer> indices = new ArrayList<>();
        int index = text.indexOf(substring);
        while (index != -1) {
            indices.add(index);
            index = text.indexOf(substring, index + substring.length());
        }
        return indices;
    }

    public int[] findMaxConsecutiveSubsequence(List<Integer> nums) {
        int maxLength = 0;
        int start = 0;
        int end = 0;
        int currentLength = 1;

        for (int i = 1; i < nums.size(); i++) {
            if ((nums.get(i) - nums.get(i - 1)) < 225
                    && (nums.get(end) - nums.get(start)) < 225) {
                currentLength++;
                if (currentLength > maxLength) {
                    maxLength = currentLength;
                    start = i - maxLength + 1;
                    end = i;
                }
            } else {
                currentLength = 1;
            }
        }
        return start == end ?
                new int[]{nums.get(0), nums.get(0)} :
                new int[]{nums.get(start), nums.get(end)};
    }
}
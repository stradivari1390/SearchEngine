package searchengine.dto;

import java.io.IOException;

import java.util.*;

import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.exceptions.WebParserException;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

@Component
public class WebParser extends RecursiveAction {
    //    private static final long serialVersionUID = 1L;
    private static final AtomicBoolean stop = new AtomicBoolean(false);
    //    private static final int THRESHOLD = 40;
    @Getter
    @Setter
    private Site site;
    @Setter
    private static InitSiteList initSiteList;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Config config;
    private static Lemmatisator lemmatisator = new Lemmatisator();
    private static HtmlCleaner cleaner = new HtmlCleaner();
    private List<String> foundLinks;
    private static List<String> visitedLinks = new ArrayList<>();
    private List<String> toParseLinkList;
    private static Pattern root;
    private static Pattern file;
    private static Pattern pageElement;
    private static Pattern contactLink;
    @Getter
    private static Set<Lemma> lemmas = new HashSet<>();
    @Getter
    private static Set<Index> indices = new HashSet<>();
    @Getter
    private static Set<Page> pages = new HashSet<>();

    @Autowired
    public WebParser(PageRepository pageRepository, LemmaRepository lemmaRepository,
                     IndexRepository indexRepository, Config config,
                     List<String> toParseLinkList) {
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.config = config;
        this.toParseLinkList = toParseLinkList;
        foundLinks = new ArrayList<>();
    }

    @SneakyThrows
    @Override
    public void compute() {
        if (stop.get()) {
            cancel(true);
            return;
        }
        for (String link : toParseLinkList) {
            if (!visitedLinks.contains(cleanUrl(link))) {
                visitedLinks.add(cleanUrl(link));
                HashMap<String, Integer> htmlData = getHtmlAndCollectLinks(link);

                if(!foundLinks.isEmpty()) {
                    WebParser task = new WebParser(pageRepository, lemmaRepository, indexRepository,
                            config, foundLinks);
                    task.setSite(site);
                    foundLinks = new ArrayList<>();
                    task.fork();
                }

                int code = htmlData.values().iterator().next();
                String content = htmlData.keySet().iterator().next();
                Page page = new Page(site, cleanUrl(link), code, content);
                pages.add(page);  //ToDo: add to Redis
            }
        }
//        if (!pages.isEmpty()) {
//            pageRepository.saveAll(pages);
//            pages.clear();
//        }
        join();
    }

    private HashMap<String, Integer> getHtmlAndCollectLinks(String url) throws WebParserException {
        Connection.Response response;
        Document document;
        try {
            response = Jsoup.connect(url)
                    .ignoreHttpErrors(true)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer())
                    .execute();
            document = response.parse();
        } catch (IOException e) {
            throw new WebParserException("Error occurred while trying to establish connection, " +
                    "parse HTML and collect links", e);
        }
        Elements linkElements = document.select("a[href]");
        for (Element linkElement : linkElements) {
            String absUrl = linkElement.attr("abs:href");
            if (absUrl.length() > 0 && isValidLink(absUrl)
                    && !visitedLinks.contains(cleanUrl(absUrl))) foundLinks.add(absUrl);
        }

        String textHtml = document.html();
        int code = response.statusCode();
        HashMap<String, Integer> result = new HashMap<>();
        result.put(textHtml, code);
        return result;
    }

    public static void collectLemmasAndIndices () {
        for(Page page : pages) {
            TagNode node = cleaner.clean(page.getContent());
            String plainText = node.getText().toString();
            Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(plainText);
            for (Map.Entry<String, Integer> entry : lemmaRankMap.entrySet()) {
                String lemmaString = entry.getKey();
                Integer rank = entry.getValue();
                Lemma lemma = null;
                for (Lemma l : lemmas) {
                    if (l.getSite().equals(page.getSite()) && l.getLemmaString().equals(lemmaString)) {
                        lemma = l;
                        break;
                    }
                }
                if (lemma == null) {
                    lemma = new Lemma(page.getSite(), lemmaString);
                    lemmas.add(lemma);
                } else {
                    lemma.setFrequency(lemma.getFrequency() + 1);
                }
                Index index = new Index(lemma, page, rank);
                indices.add(index);
            }
        }
    }

    public static void initiateValidationPatterns() {
        StringBuilder rootPatterns = new StringBuilder();
        for (searchengine.config.Site initSite : initSiteList.getSites()) {
            rootPatterns.append("^").append(initSite.getUrl()).append("|");
        }
        String rootPattern = rootPatterns.deleteCharAt(rootPatterns.length() - 1).toString();
        root = Pattern.compile(rootPattern);
        file = Pattern.compile("(\\.(?i)(jpg|bmp|png|gif|pdf|doc|xls|ppt" +
                "|jpeg|zip|tar|jar|gz|svg|pptx|docx|xlsx))$");
        pageElement = Pattern.compile("#");
        contactLink = Pattern.compile("(?i)(tel:|tg:|mailto:)");
    }

    private boolean isValidLink(String link) {
        return !file.matcher(link).find() &&
                !pageElement.matcher(link).find() &&
                !contactLink.matcher(link).find() &&
                root.matcher(link.replaceAll("/w{3}\\.", "/")).lookingAt();
    }

    public void addPage(String url) throws WebParserException {
        boolean isNewPage = false;
        HashMap<String, Integer> html = getHtmlAndCollectLinks(url);

        int code = html.values().iterator().next();
        String content = html.keySet().iterator().next();

        Page page = pageRepository.findByPath(url);
        if (page == null) {
            isNewPage = true;
            page = new Page();
        } else if (page.getCode() >= 400) {
            isNewPage = true;
        }
        page.setCode(code);
        page.setPath(cleanUrl(url));
        page.setContent(content);
        page.setSite(site);
        pageRepository.save(page);
        if (code < 400) {
            TagNode node = cleaner.clean(content);
            String plainText = node.getText().toString();
            Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(plainText);
            boolean finalIsNewPage = isNewPage;
            Page finalPage = page;
            lemmaRankMap.forEach((lemmaString, rank) -> {
                Lemma lemma;
                lemma = lemmaRepository.findLemmaByLemmaStringAndSite(lemmaString, site);
                if (lemma == null) lemma = new Lemma(site, lemmaString);
                else lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
                Index index;
                if (finalIsNewPage) index = new Index(lemma, finalPage, rank);
                else {
                    index = indexRepository.findByLemmaAndPage(lemma, finalPage);
                    if (index == null) index = new Index(lemma, finalPage, rank);
                    else index.setRank(rank);
                }
                indexRepository.save(index);
            });
        }
    }

    public String cleanUrl(String url) {
        String cleanUrl = url;
        if (url.contains("?")) {
            cleanUrl = url.substring(0, url.indexOf("?"));
        }
        return cleanUrl;
    }

    public static void stopCrawling() {
        stop.set(true);
    }

    public static void clearVisitedLinks() {
        visitedLinks.clear();
    }

    public static void clearLemmasList() {
        lemmas.clear();
    }

    public static void clearIndicesSet() {
        indices.clear();
    }

    public static void clearPagesSet() {
        pages.clear();
    }
}
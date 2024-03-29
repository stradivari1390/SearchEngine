package searchengine.services.parsing;

import java.io.IOException;

import java.util.*;

import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import org.apache.tomcat.util.http.fileupload.impl.InvalidContentTypeException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
import searchengine.util.ConcurrentHashSet;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WebParser extends RecursiveTask<Integer> {
    private static final AtomicBoolean stop = new AtomicBoolean(false);
    private static final int THRESHOLD = 100;
    @Getter
    @Setter
    private Site site;
    private final InitSiteList initSiteList;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Config config;
    private Lemmatisator lemmatisator;
    private Set<String> foundLinks;
    private static ConcurrentHashSet<String> visitedLinks = new ConcurrentHashSet<>(10000, 0.75F, 16);
    private final List<String> toParseLinkList;
    private static Pattern root;
    private static Pattern pageElement;
    private static Pattern contactLink;
    private int amount;
    private final Object lemmaLock = new Object();

    @Autowired
    public WebParser(InitSiteList initSiteList, PageRepository pageRepository, LemmaRepository lemmaRepository,
                     IndexRepository indexRepository, Config config,
                     Lemmatisator lemmatisator, List<String> toParseLinkList) {
        this.initSiteList = initSiteList;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.config = config;
        this.lemmatisator = lemmatisator;
        this.toParseLinkList = toParseLinkList;
        foundLinks = new HashSet<>();
        amount = 0;
    }

    @SneakyThrows
    @Override
    public Integer compute() {
        List<Page> pagesToSave = new ArrayList<>();
        for (String link : toParseLinkList) {
            if (stop.get()) {
                break;
            }
            if (visitedLinks.contains(link)) {
                continue;
            }
            visitedLinks.add(link);
            Map.Entry<String, Integer> htmlData = getHtmlAndCollectLinks(link);
            if (htmlData == null) {
                continue;
            }
            Page page = new Page(site, link, htmlData.getValue(), htmlData.getKey());
            pagesToSave.add(page);
            amount++;
        }
        saveLemmasAndIndices(pagesToSave);
        if (!foundLinks.isEmpty()) {
            processFoundLinks();
        }
        return amount;
    }

    @Transactional
    protected void saveLemmasAndIndices(List<Page> pagesToSave) {
        Map<String, Lemma> uniqueLemmas = new HashMap<>();
        List<Index> indicesToSave = new ArrayList<>();
        for (Page page : pagesToSave) {
            Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(page.getContent());
            for (Map.Entry<String, Integer> entry : lemmaRankMap.entrySet()) {
                if (stop.get()) {
                    break;
                }
                int rank = entry.getValue();
                Lemma lemma = addOrUpdateLemma(uniqueLemmas, entry.getKey());
                Index index = new Index(lemma, page, rank);
                indicesToSave.add(index);
            }
        }
        pageRepository.saveAll(pagesToSave);
        synchronized (lemmaLock) {
            lemmaRepository.saveAll(uniqueLemmas.values());
        }
        indexRepository.saveAll(indicesToSave);
    }

    private Lemma addOrUpdateLemma(Map<String, Lemma> uniqueLemmas, String lemmaString) {
        Lemma lemma = uniqueLemmas.get(lemmaString);
        synchronized (lemmaLock) {
            if (lemma == null) {
                lemma = lemmaRepository.findLemmaByLemmaStringAndSite(lemmaString, site);
                if (lemma != null) {
                    lemma.setFrequency(lemma.getFrequency() + 1);
                } else {
                    lemma = new Lemma(site, lemmaString);
                }
                uniqueLemmas.put(lemmaString, lemma);
            } else {
                Lemma lemmaFromRepo = lemmaRepository.findLemmaByLemmaStringAndSite(lemmaString, site);
                if (lemmaFromRepo != null) {
                    lemmaFromRepo.setFrequency(lemma.getFrequency() + lemmaFromRepo.getFrequency());
                    lemma = lemmaFromRepo;
                } else lemma.setFrequency(lemma.getFrequency() + 1);
            }
        }
        return lemma;
    }

    private void processFoundLinks() {
        List<String> linksToProcess = new ArrayList<>(foundLinks);
        for (int i = 0; i < linksToProcess.size(); i += THRESHOLD) {
            if (stop.get()) {
                break;
            }
            int end = Math.min(linksToProcess.size(), i + THRESHOLD);
            List<String> subList = linksToProcess.subList(i, end);
            WebParser task = new WebParser(initSiteList, pageRepository, lemmaRepository, indexRepository,
                    config, lemmatisator, subList);
            task.setSite(site);
            task.fork();
            amount += task.join();
        }
    }

    private Map.Entry<Document, Integer> getPageDocumentView(String url) throws WebParserException {
        Connection.Response response;
        Document document;
        try {
            response = Jsoup.connect(url)
                    .ignoreHttpErrors(true)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer())
                    .execute();
            String contentType = response.contentType();
            if (contentType != null && (contentType.matches("^(?:application|image|audio|video)"))) {
                throw new WebParserException("URL is a FILE", new InvalidContentTypeException());
            }
            document = response.parse();
        } catch (IOException e) {
            throw new WebParserException(url + " -- Error occurred while trying to establish connection, " +
                    "parse HTML and collect links", e);
        }
        return new AbstractMap.SimpleEntry<>(document, response.statusCode());
    }

    private Map.Entry<String, Integer> getHtmlAndCollectLinks(String url) {
        Map.Entry<Document, Integer> documentPageView = null;
        try {
            documentPageView = getPageDocumentView(url);
        } catch (WebParserException e) {
            return null;
        }
        Elements linkElements = documentPageView.getKey().select("a[href], link[href]");
        for (Element linkElement : linkElements) {
            String absUrl = cleanUrl(linkElement.attr("abs:href"));
            if (absUrl.length() > 0 && isValidLink(absUrl)) {
                foundLinks.add(absUrl);
            }
        }
        String textHtml = documentPageView.getKey().html();
        int code = documentPageView.getValue();
        return new AbstractMap.SimpleEntry<>(textHtml, code);
    }


    public static void initiateValidationPatterns(InitSiteList initSiteList) {
        StringBuilder rootPatterns = new StringBuilder();
        for (searchengine.config.Site initSite : initSiteList.getSites()) {
            rootPatterns.append("^").append(initSite.getUrl()).append("|");
        }
        String rootPattern = rootPatterns.deleteCharAt(rootPatterns.length() - 1).toString();
        root = Pattern.compile(rootPattern);
        pageElement = Pattern.compile("#");
        contactLink = Pattern.compile("(?i)(tel:|tg:|mailto:)");
    }

    public static boolean isValidLink(String link) {
        return !pageElement.matcher(link).find() &&
                !contactLink.matcher(link).find() &&
                root.matcher(link.replaceAll("/w{3}\\.", "/")).lookingAt();
    }

    public Map.Entry<Page, Boolean> addPage(String url) {
        boolean newPage = false;
        Map.Entry<String, Integer> html = getHtmlAndCollectLinks(url);
        if (html == null) {
            return null;
        }
        int statusCode = html.getValue();
        String content = html.getKey();

        Page page = pageRepository.findByPath(url);
        if (page == null) {
            page = new Page();
            newPage = true;
        }
        page.setCode(statusCode);
        page.setPath(cleanUrl(url));
        page.setContent(content);
        page.setSite(site);

        pageRepository.save(page);
        return new AbstractMap.SimpleEntry<>(page, newPage);
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

    public static void startCrawling() {
        stop.set(false);
    }

    public static boolean stopped() {
        return stop.get();
    }

    public static void clearVisitedLinks() {
        visitedLinks.clear();
    }
}
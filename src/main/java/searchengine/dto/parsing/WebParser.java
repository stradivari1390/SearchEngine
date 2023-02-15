package searchengine.dto.parsing;

import java.io.IOException;

import java.util.*;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.exceptions.WebParserException;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WebParser extends RecursiveTask<Integer> {
    private static final long serialVersionUID = 1L;  // Do I really need to serialize it and fields?
//    These are two patterns to match links in script pages, second one consumes huge amount of memory
//    private static final String URL_REGEX1 = "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
//    private static final String URL_REGEX2 = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))");
    private static final AtomicBoolean stop = new AtomicBoolean(false);
    private static final int THRESHOLD = 25;
    @Getter
    @Setter
    private Site site;
    private final InitSiteList initSiteList;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Config config;
    private Lemmatisator lemmatisator;
    private List<String> foundLinks;
    private static List<String> visitedLinks = new CopyOnWriteArrayList<>();
    private List<String> toParseLinkList;
    private static Pattern root;
    private static Pattern file;
    private static Pattern pageElement;
    private static Pattern contactLink;
    private final RedisTemplate<String, Page> redisTemplate;
    public static final String REDIS_KEY = "pages";
    //    private static final Object lock = new Object();
    private static AtomicInteger count = new AtomicInteger(0);
    private int amount;

    @Autowired
    public WebParser(InitSiteList initSiteList, PageRepository pageRepository, LemmaRepository lemmaRepository,
                     IndexRepository indexRepository, Config config,
                     Lemmatisator lemmatisator, List<String> toParseLinkList, RedisTemplate<String, Page> redisTemplate) {
        this.initSiteList = initSiteList;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.config = config;
        this.lemmatisator = lemmatisator;
        this.toParseLinkList = toParseLinkList;
        foundLinks = new ArrayList<>();
        this.redisTemplate = redisTemplate;
        amount = 0;
    }

    @SneakyThrows
    @Override
    public Integer compute() {
        for (String link : toParseLinkList) {
            if (!visitedLinks.contains(cleanUrl(link))) {
                synchronized (stop) {
                    if (stop.get()) {
                        cancel(true);
                        return 0;
                    }
                }
                visitedLinks.add(cleanUrl(link));
                Map.Entry<String, Integer> htmlData = getHtmlAndCollectLinks(link);
                Page page = new Page(site, cleanUrl(link), htmlData.getValue(), htmlData.getKey());
                redisTemplate.opsForList().rightPush(REDIS_KEY, page);
                amount++;
                System.out.print("\r pages done: " + count.incrementAndGet());

                if (!foundLinks.isEmpty()) {
                    for (int i = 0; i < foundLinks.size(); i += THRESHOLD) {
                        int end = Math.min(foundLinks.size(), i + THRESHOLD);
                        List<String> subList = foundLinks.subList(i, end);
                        WebParser task = new WebParser(initSiteList, pageRepository, lemmaRepository, indexRepository,
                                config, lemmatisator, subList, redisTemplate);
                        task.setSite(site);
                        task.fork();
                        amount += task.join();
                    }
                    foundLinks.clear();
                }
            }
        }
        return amount;
    }

    @SneakyThrows
    private Map.Entry<Document, Integer> getPageDocumentView(String url) {
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
            throw new WebParserException(url + " -- Error occurred while trying to establish connection, " +
                    "parse HTML and collect links", e);
        }
        return new AbstractMap.SimpleEntry<>(document, response.statusCode());
    }

    private Map.Entry<String, Integer> getHtmlAndCollectLinks(String url) {
        Map.Entry<Document, Integer> documentPageView = getPageDocumentView(url);
        Elements linkElements = documentPageView.getKey().select("a[href]");
        for (Element linkElement : linkElements) {
            String absUrl = linkElement.attr("abs:href");
            if (absUrl.length() > 0 && isValidLink(absUrl)) foundLinks.add(absUrl);
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
        file = Pattern.compile("([\\.-](?i)(jpg|bmp|png|gif|pdf|doc|xls|ppt" +
                "|jpeg|zip|tar|jar|gz|svg|pptx|docx|xlsx))$");
        pageElement = Pattern.compile("#");
        contactLink = Pattern.compile("(?i)(tel:|tg:|mailto:)");
    }

    public static boolean isValidLink(String link) {
        return !file.matcher(link).find() &&
                !pageElement.matcher(link).find() &&
                !contactLink.matcher(link).find() &&
                root.matcher(link.replaceAll("/w{3}\\.", "/")).lookingAt();
    }

    public Map.Entry<Page, Boolean> addPage(String url) {
        boolean newPage = false;
        Map.Entry<String, Integer> html = getHtmlAndCollectLinks(url);
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

    public static void clearVisitedLinks() {
        visitedLinks.clear();
    }
}
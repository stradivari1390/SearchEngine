package searchengine.dto;

import java.io.IOException;

import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private transient Site site;
    private String url;
    @Setter
    private static InitSiteList initSiteList;
    private final transient PageRepository pageRepository;
    private final transient LemmaRepository lemmaRepository;
    private final transient IndexRepository indexRepository;
    private final transient Config config;
    private final transient HtmlCleaner cleaner;
    private final Set<String> links = new HashSet<>();
    private Set<String> visitedLinks;
    private static Pattern root;
    private static Pattern file;
    private static Pattern pageElement;
    private static Pattern contactLink;

    @Autowired
    public WebParser(Set<String> visitedLinks,
                     PageRepository pageRepository, LemmaRepository lemmaRepository,
                     IndexRepository indexRepository, Config config) {
        this.visitedLinks = visitedLinks;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.config = config;

        cleaner = new HtmlCleaner();
        WebParsersStorage.getInstance().add(this);
    }

    @SneakyThrows
    @Override
    public void compute() {
        if (WebParsersStorage.getInstance().isTerminationInProcess().get()
                || visitedLinks.contains(cleanUrl(url))) {
            return;
        }
        visitedLinks.add(cleanUrl(url));
        HashMap<String, Integer> htmlData = getHtmlAndCollectLinks(url);
        int code = htmlData.values().iterator().next();
        String content = htmlData.keySet().iterator().next();

        Page page = new Page(site, cleanUrl(url), code, content);
        pageRepository.save(page);

        TagNode node = cleaner.clean(content);
        String plainText = node.getText().toString();
        addLemmaAndIndex(plainText, page, true);

        List<WebParser> parsers = links.stream()
                .map(link -> {
                    WebParser parser = new WebParser(visitedLinks, pageRepository,
                            lemmaRepository, indexRepository, config);
                    parser.setSite(site, link);
                    parser.fork();
                    return parser;
                })
                .collect(Collectors.toList());

        for (WebParser parser : parsers) {
            parser.join();
            WebParsersStorage.getInstance().remove(parser);
        }
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
            throw new WebParserException("Error occurred while trying to parse HTML and collect links", e);
        }
        Elements linkElements = document.select("a[href]");
        for (Element linkElement : linkElements) {
            String absUrl = linkElement.attr("abs:href");
            if (absUrl.length() > 0 && isValidLink(absUrl)) links.add(absUrl);
        }

        String textHtml = document.html();
        int code = response.statusCode();
        HashMap<String, Integer> result = new HashMap<>();
        result.put(textHtml, code);
        return result;
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

        int code = (int) html.values().toArray()[0];
        String content = String.valueOf(html.keySet().toArray()[0]);

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
            addLemmaAndIndex(plainText, page, isNewPage);
        }
    }

    @SneakyThrows
    private void addLemmaAndIndex(String text, Page page, boolean isNewPage) {
        Lemmatisator lemmatisator = Lemmatisator.getInstance();
        Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(text);
        lemmaRankMap.forEach((lemmaString, rank) -> {
            Lemma lemma;
            synchronized (lemmaRepository) {
                lemma = lemmaRepository.findLemmaByLemmaStringAndSite(lemmaString, page.getSite());

                if (lemma == null) {
                    lemma = new Lemma(page.getSite(), lemmaString);
                } else {
                    lemma.setFrequency(lemma.getFrequency() + 1);
                }
                lemmaRepository.save(lemma);
            }
            Index index;
            if (isNewPage) {
                index = new Index(lemma, page, rank);
            } else {
                index = indexRepository.findByLemmaAndPage(lemma, page);
                if (index == null) {
                    index = new Index(lemma, page, rank);
                } else {
                    index.setRank(rank);
                }
            }
            indexRepository.save(index);
        });
    }

    public String cleanUrl(String url) {
        String cleanUrl = url;
        if (url.contains("?")) {
            cleanUrl = url.substring(0, url.indexOf("?"));
        }
        return cleanUrl;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site, String url) {
        this.site = site;
        this.url = url;
    }
}
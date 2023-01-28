package searchengine.dto;

import java.io.IOException;

import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

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
    private final transient Site site;
    private final String url;
    @Autowired
    private transient InitSiteList initSiteList;
    @Autowired
    private transient PageRepository pageRepository;
    @Autowired
    private transient LemmaRepository lemmaRepository;
    @Autowired
    private transient IndexRepository indexRepository;
    @Autowired
    private transient Config config;
    @Autowired
    private transient Lemmatisator lemmatisator;

    transient HtmlCleaner cleaner;
    private final Set<String> links = new HashSet<>();
    private Set<String> visitedLinks;

    public WebParser() {
        site = new Site();
        url = "";
    }

    public WebParser(String url, Site site, Set<String> visitedLinks) {

        this.url = url;
        this.site = site;

        this.visitedLinks = visitedLinks;

        cleaner = new HtmlCleaner();

        WebParsersStorage.getInstance().add(this);
    }

    @SneakyThrows
    @Override
    protected void compute() {
        while (!WebParsersStorage.getInstance().isTerminationInProcess().get()) {
            if (visitedLinks.contains(cleanUrl(url))) {
                return;
            }
            visitedLinks.add(cleanUrl(url));
            HashMap<String, Integer> html = getHtmlAndCollectLinks(url);
            int code = (int) html.values().toArray()[0];
            String content = String.valueOf(html.keySet().toArray()[0]);

            Page page = new Page(site, cleanUrl(url), code, content);
            pageRepository.save(page);

            TagNode node = cleaner.clean(content);
            String plainText = cleaner.getInnerHtml(node);
            addLemmaAndIndex(plainText, page, true);

            List<WebParser> parsers = new ArrayList<>();
            for (String link : links) {
                WebParser parser = new WebParser(link, site, visitedLinks);
                parser.fork();
                parsers.add(parser);
            }
            for (WebParser parser : parsers) {
                parser.join();
                WebParsersStorage.getInstance().remove(parser);
            }
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

    private boolean isValidLink(String link) {
        StringBuilder rootPatterns = new StringBuilder();
        for (searchengine.config.Site initSite : initSiteList.getSites()) {
            rootPatterns.append("^").append(initSite.getUrl()).append("|");
        }
        String rootPattern = rootPatterns.deleteCharAt(rootPatterns.length() - 1).toString();
        Pattern root = Pattern.compile(rootPattern);
        Pattern file = Pattern.compile("(\\.(?i)(jpg|bmp|png|gif|pdf|doc|xls|ppt" +
                "|jpeg|zip|tar|jar|gz|svg|pptx|docx|xlsx))$");
        Pattern pageElement = Pattern.compile("#");
        Pattern contactLink = Pattern.compile("(?i)(tel:|tg:|mailto:)");

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
            String plainText = cleaner.getInnerHtml(node);
            addLemmaAndIndex(plainText, page, isNewPage);
        }
    }

    private void addLemmaAndIndex(String text, Page page, boolean isNewPage) {
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
}
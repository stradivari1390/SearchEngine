package searchengine.dto;

import java.io.IOException;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

public class WebParser extends RecursiveTask<Integer> {
    private final Site site;
    private final InitSiteList initSiteList;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Config config;
    private final Lemmatisator lemmatisator;
    private final String url;
    private final String userAgent;
    private final String referrer;
    private int pagesCount = 0;
    HtmlCleaner cleaner;
    private HashSet<String> links = new HashSet<>();
    private HashSet<String> visitedLinks;

    public WebParser(String url, Site site, InitSiteList initSiteList, PageRepository pageRepository,
                     LemmaRepository lemmaRepository, IndexRepository indexRepository,
                     Config config, Lemmatisator lemmatisator, HashSet<String> visitedLinks) {
        this.url = url;
        this.site = site;
        this.initSiteList = initSiteList;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.config = config;
        this.lemmatisator = lemmatisator;

        this.userAgent = config.getUserAgent();
        this.referrer = config.getReferrer();

        this.visitedLinks = visitedLinks;

        cleaner = new HtmlCleaner();
    }


    @Override
    protected Integer compute() {
//        synchronized (visitedLinks) {
            if (visitedLinks.contains(cleanUrl(url))) {
                return 0;
            }
            visitedLinks.add(cleanUrl(url));
//        }
        HashMap<String, Integer> html = getHtmlAndCollectLinks(url);
        int code = (int) html.values().toArray()[0];
        String content = String.valueOf(html.keySet().toArray()[0]);

        if (html != null) {
            Page page = new Page(site, cleanUrl(url), code, content);
            pageRepository.save(page);
            pagesCount++;

            TagNode node = cleaner.clean(content);
            String plainText = cleaner.getInnerHtml(node);
            addLemmaAndIndex(plainText, page, true);

            List<WebParser> parsers = new ArrayList<>();
            for (String link : links) {
                WebParser parser = new WebParser(link, site, initSiteList, pageRepository,
                        lemmaRepository, indexRepository, config, lemmatisator, visitedLinks);
                parser.fork();
                parsers.add(parser);
            }
            for (WebParser parser : parsers) {
                pagesCount += parser.join();
            }
        }
        return pagesCount;
    }

    private HashMap<String, Integer> getHtmlAndCollectLinks(String url) {
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
            throw new RuntimeException(e);
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
        for(searchengine.config.Site initSite : initSiteList.getSites()) {
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
                root.matcher(link.replaceAll("/www\\.", "/")).lookingAt();
    }

    public void addPage(String url) {
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
            Lemma lemma = lemmaRepository.findLemmaByLemmaAndSite(lemmaString, page.getSite());
            if (lemma == null) {
                lemma = new Lemma(page.getSite(), lemmaString);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmaRepository.save(lemma);
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
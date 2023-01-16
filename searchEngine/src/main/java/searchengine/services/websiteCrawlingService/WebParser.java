package searchengine.services.websiteCrawlingService;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.Config;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.lemmatisationService.Lemmatisator;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;

public class WebParser extends RecursiveTask<Integer> {

    private final Site site;
    private final SitemapNode node;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Config config;
    private final Lemmatisator lemmatisator;
    private int pageCount;

    public WebParser(Site site, SitemapNode sitemapNode,
                     SiteRepository siteRepository, PageRepository pageRepository,
                     LemmaRepository lemmaRepository, IndexRepository indexRepository,
                     Config config, Lemmatisator lemmatisator) {
        pageCount = 0;
        this.site = site;
        this.node = sitemapNode;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.config = config;
        this.lemmatisator = lemmatisator;
    }

    @Override
    protected Integer compute() {
        try {
            Connection.Response response = Jsoup.connect(node.getUrl())
                    .ignoreHttpErrors(true)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer())
                    .execute();

            Document document = response.parse();

            addPage(response, document);
            processLinks(document);
        } catch (IOException exception) {
            site.setLastError("Indexing stopped");
            site.setStatus(StatusType.FAILED);
            siteRepository.save(site);
            System.out.println(exception.getMessage());
        }
        return pageCount;
    }

    private void processLinks(Document document) {
        Elements links = document.select("body").select("a");
        for (Element link : links) {
            String childUrl = link.absUrl("href");
            if (isCorrectLink(childUrl)) {
                childUrl = childUrl.replaceAll("\\?.+", "");
                node.addChild(new SitemapNode(childUrl));
                pageCount++;
            }
        }

        for (SitemapNode child : node.getChildren()) {
            WebParser task = new WebParser(site, child,
                    siteRepository, pageRepository, lemmaRepository, indexRepository,
                    config, lemmatisator);
            task.compute();
        }
    }

    public void addPage() throws IOException {

        Connection.Response response = Jsoup.connect(node.getUrl())
                .userAgent(config.getUserAgent())
                .referrer(config.getReferrer())
                .ignoreHttpErrors(true)
                .execute();

        addPage(response, response.parse());
    }

    private void addPage(Connection.Response response, Document document) {

        boolean isNewPage = false;
        Page page = pageRepository.findByPath(node.getUrl());
        if (page == null) {
            isNewPage = true;
            page = new Page();
        } else if (page.getCode() >= 400) {
            isNewPage = true;
        }

        page.setCode(response.statusCode());
        page.setPath(node.getUrl());
        page.setContent(document.html());
        page.setSite(site);

        pageRepository.save(page);

        if (response.statusCode() < 400) {
            addLemmaAndIndex(document, page, isNewPage);
        }
    }

    private void addLemmaAndIndex(Document document, Page page, boolean isNewPage) {

        String textOnly = Jsoup.parse(document.html()).text();
        Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(textOnly);

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

    private boolean isCorrectLink(String url) {

        Pattern root = Pattern.compile("^" + node.getUrl().replace("/www.", "/"));
        Pattern file = Pattern.compile("(\\.(?i)(jpg|bmp|png|gif|pdf|doc|xls|ppt|jpeg|zip|tar|jar|gz|svg|pptx|docx|xlsx))$");
        Pattern pageElement = Pattern.compile("#");
        Pattern contactLink = Pattern.compile("(?i)(tel:|tg:|mailto:)");

        return !file.matcher(url).find() &&
                !pageElement.matcher(url).find() &&
                !contactLink.matcher(url).find() &&
                root.matcher(url.replace("/www.", "/")).lookingAt();
    }

    public Site getSite() {
        return site;
    }
}
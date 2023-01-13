package searchengine.services.websiteCrawlingService;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
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

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WebParser extends RecursiveTask<Integer> {

    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private Config config;
    private int pageCount;
    private Site site;
    private SitemapNode node;
    @Autowired
    private Lemmatisator lemmatisator;

    public WebParser() {
        pageCount = 0;
    }

    public WebParser(Site site, SitemapNode sitemapNode,
                     SiteRepository siteRepository, PageRepository pageRepository,
                     LemmaRepository lemmaRepository, IndexRepository indexRepository,
                     Config config, Lemmatisator lemmatisator) {
        this();
        pageCount++;
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

            // ToDo: Try Thread.sleep(750);  ?  no difference.

            addPage(response, document);

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
        } catch (IOException exception) {
            site.setLastError("Остановка индексации");
            site.setStatus(StatusType.FAILED);
            siteRepository.save(site);
//                 exception.printStackTrace();
            System.out.println(exception.getMessage());
        }
        return pageCount;
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
package searchengine.services;

import lombok.SneakyThrows;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.dto.responses.ErrorResponse;
import searchengine.services.parsing.Lemmatisator;
import searchengine.model.*;
import searchengine.repository.*;
import searchengine.services.parsing.WebParser;
import searchengine.dto.responses.IndexResponse;
import searchengine.dto.responses.Response;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IndexingService {
    private static final Logger logger = LogManager.getLogger(IndexingService.class);
    private static final String HTTP_S_WWW = "^(https?://)?(www\\.)?";
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final InitSiteList initSiteList;
    private final Config config;
    private static AtomicBoolean indexing = new AtomicBoolean(false);
    private final Lemmatisator lemmatisator;

    @Autowired
    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository,
                           LemmaRepository lemmaRepository, IndexRepository indexRepository,
                           InitSiteList initSiteList, Config config, Lemmatisator lemmatisator) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.initSiteList = initSiteList;
        this.config = config;
        this.lemmatisator = lemmatisator;
        WebParser.initiateValidationPatterns(initSiteList);
    }

    @Transactional
    protected void clearData() {
        indexRepository.deleteAllInBatch();
        lemmaRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        siteRepository.deleteAllInBatch();
        WebParser.clearVisitedLinks();
    }

    @SneakyThrows
    public Response startIndexing() {
        if (indexing.compareAndSet(false, true)) {
            clearData();
            WebParser.startCrawling();
            new Thread(this::indexing).start();
            return new IndexResponse(true);
        } else return new ErrorResponse(false, "Индексация уже запущена");
    }

    public void indexing() {
        List<WebParser> webParserList = createWebParsers(initSiteList.getSites());
        webParserList.forEach(webParser -> new Thread(() -> indexingThreadProcess(webParser)).start());
    }

    private void indexingThreadProcess(WebParser webParser) {
        Site site = webParser.getSite();
        try {
            saveSiteStatus(site, StatusType.INDEXING);
            ForkJoinPool forkJoinPool = new ForkJoinPool();
            forkJoinPool.execute(webParser);
            int count = webParser.join();
            logger.info(webParser.getSite().getName() + ": " + count + " pages processed.");
            if (!WebParser.stopped()) {
                saveSiteStatus(site, StatusType.INDEXED);
            }
        } catch (CancellationException e) {
            e.printStackTrace();
            site.setLastError("Ошибка индексации: " + e.getMessage());
            saveSiteStatus(site, StatusType.FAILED);
        }
    }

    public Response stopIndexing() {
        if (indexing.compareAndSet(true, false)) {
            siteRepository.findAllByStatus(StatusType.INDEXING)
                    .forEach(s -> {
                        s.setLastError("Индексация прервана пользователем");
                        s.setStatus(StatusType.FAILED);
                        siteRepository.save(s);
                    });
            WebParser.stopCrawling();
            return new IndexResponse(true);
        } else {
            return new ErrorResponse(false, "Индексация не запущена");
        }
    }

    @SneakyThrows
    public Response indexPage(String url) {
        if (url.isEmpty()) {
            return new ErrorResponse(false, "Требуется ввести URL");
        } else if (!WebParser.isValidLink(url)) {
            return new ErrorResponse(false, "Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }
        Site site;
        StatusType initStatusType = null;
        Pattern pattern = Pattern.compile("^(?:https?:\\/\\/)?(?:www\\.)?([a-zA-Z0-9-]+\\.[a-zA-Z]{2,})(?:$|\\/)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String domainName = matcher.group();
            site = siteRepository.findSiteByUrl(domainName.replaceAll("/w{3}\\.", "/"));
            initStatusType = site.getStatus();
        } else {
            site = createNewSite(url);
        }
        if (initStatusType == StatusType.INDEXING) {
            return new ErrorResponse(false, "Индексация уже запущена");
        }
        assert site != null;
        saveSiteStatus(site, StatusType.INDEXING);
        WebParser webParser = newWebParse(site);
        Map.Entry<Page, Boolean> pageEntry = webParser.addPage(url);
        if (pageEntry == null) {
            return new ErrorResponse(false, "Требуется ввести корректный URL, " +
                    "можете попробовать скопировать его из адресной строки браузера");
        }
        Page page = pageEntry.getKey();
        Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(page.getContent());
        for (Map.Entry<String, Integer> entry : lemmaRankMap.entrySet()) {
            String lemmaString = entry.getKey();
            int rank = entry.getValue();
            Lemma lemma = updateOrCreateLemma(site, lemmaString);
            Index index = indexRepository.findByLemmaAndPage(lemma, page);
            if (index == null) {
                index = new Index(lemma, page, rank);
            } else {
                index.setRank(rank);
            }
            indexRepository.save(index);
        }
        saveSiteStatus(site, initStatusType == null ? StatusType.INDEXED : initStatusType);
        return new IndexResponse(true);
    }

    private Site createNewSite(String url) {
        for (searchengine.config.Site initSite : initSiteList.getSites()) {
            if (url.replaceAll(HTTP_S_WWW, "")
                    .contains(initSite.getUrl().replaceAll(HTTP_S_WWW, ""))) {
                Site site = new Site(initSite.getUrl(), initSite.getName());
                saveSiteStatus(site, StatusType.FAILED);
                return site;
            }
        }
        return null;
    }

    private Lemma updateOrCreateLemma(Site site, String lemmaString) {
        Lemma lemma = lemmaRepository.findLemmaByLemmaStringAndSite(lemmaString, site);
        if (lemma == null) {
            lemma = new Lemma(site, lemmaString);
            lemmaRepository.save(lemma);
        }
        return lemma;
    }

    public static boolean isIndexing() {
        return indexing.get();
    }

    private WebParser newWebParse(Site site) {
        WebParser webParser = new WebParser(initSiteList, pageRepository, lemmaRepository, indexRepository, config,
                lemmatisator, Collections.singletonList(site.getUrl()));
        webParser.setSite(site);
        return webParser;
    }

    private List<WebParser> createWebParsers(List<searchengine.config.Site> initSites) {
        List<WebParser> webParserList = new ArrayList<>();
        for (searchengine.config.Site initSite : initSites) {
            Site site = siteRepository.findSiteByUrl(initSite.getUrl());
            if (site == null) {
                site = new Site(initSite.getUrl(), initSite.getName());
            }
            webParserList.add(newWebParse(site));
        }
        return webParserList;
    }

    private void saveSiteStatus(Site site, StatusType status) {
        site.setStatus(status);
        siteRepository.save(site);
    }
}
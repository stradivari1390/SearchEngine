package searchengine.services;

import lombok.SneakyThrows;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.dto.parsing.Lemmatisator;
import searchengine.exceptions.IndexingException;
import searchengine.model.*;
import searchengine.repository.*;
import searchengine.dto.parsing.WebParser;
import searchengine.responses.IndexResponse;
import searchengine.responses.Response;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ObjIntConsumer;

@Service
public class IndexingService {
    Logger logger = LogManager.getLogger(IndexingService.class);
    private static final String RESULT = "result";
    private static final String ERROR = "error";
    private static final String HTTP_S_WWW = "^(https?://)?(www\\.)?";
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final InitSiteList initSiteList;
    private final RedisTemplate<String, Page> redisTemplatePage;
    private final RedisTemplate<String, Lemma> redisTemplateLemma;
    private final RedisTemplate<String, Index> redisTemplateIndex;
    private final Config config;
    private static AtomicBoolean indexing = new AtomicBoolean(false);
    private List<ForkJoinPool> forkJoinPoolList;
    private List<Thread> threadList;
    private final Lemmatisator lemmatisator;

    @Autowired
    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository,
                           LemmaRepository lemmaRepository, IndexRepository indexRepository,
                           InitSiteList initSiteList, RedisTemplate<String, Page> redisTemplatePage,
                           RedisTemplate<String, Lemma> redisTemplateLemma, RedisTemplate<String, Index> redisTemplateIndex,
                           Config config, Lemmatisator lemmatisator) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.initSiteList = initSiteList;
        this.redisTemplatePage = redisTemplatePage;
        this.redisTemplateLemma = redisTemplateLemma;
        this.redisTemplateIndex = redisTemplateIndex;
        this.config = config;
        this.lemmatisator = lemmatisator;
        WebParser.initiateValidationPatterns(initSiteList);
    }

    private void clearData() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    @SneakyThrows
    public Response startIndexing() {
        if (indexing.compareAndSet(false, true)) {
            new Thread(this::indexing).start();
            return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
        } else {
            return new IndexResponse(new JSONObject().put(RESULT, false)
                    .put(ERROR, "Индексация уже запущена"), HttpStatus.BAD_REQUEST);
        }
    }

    public void indexing() {
        threadList = new ArrayList<>();
        forkJoinPoolList = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(initSiteList.getSites().size());
        clearData();
        List<WebParser> webParserList = createWebParsers(initSiteList.getSites());
        webParserList.forEach(webParser -> threadList.add(new Thread(() -> indexingThreadProcess(webParser, latch))));
        threadList.forEach(Thread::start);
        try {
            latch.await();

            proceedPagesFromRedis(initSiteList.getBatchsize());
            proceedLemmasFromRedis(initSiteList.getBatchsize());
            proceedIndicesFromRedis(initSiteList.getBatchsize());

            WebParser.clearVisitedLinks();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexingException("An error occurred while indexing", e);
        }
    }

    private void indexingThreadProcess(WebParser webParser, CountDownLatch latch) {
        Site site = webParser.getSite();
        try {
            saveSiteStatus(site, StatusType.INDEXING);
            ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            forkJoinPoolList.add(forkJoinPool);
            forkJoinPool.execute(webParser);
            int count = webParser.join();
            logger.info(webParser.getSite().getName() + ": " + count + " pages processed.");
            saveSiteStatus(site, StatusType.INDEXED);
            latch.countDown();
        } catch (CancellationException e) {
            e.printStackTrace();
            site.setLastError("Ошибка индексации: " + e.getMessage());
            saveSiteStatus(site, StatusType.FAILED);
        }
    }

    @SneakyThrows
    public Response stopIndexing() {
        if (indexing.compareAndSet(true, false)) {
            CompletableFuture.runAsync(() -> {
                WebParser.stopCrawling();

                proceedPagesFromRedis(initSiteList.getBatchsize());
                proceedLemmasFromRedis(initSiteList.getBatchsize());
                proceedIndicesFromRedis(initSiteList.getBatchsize());

                WebParser.clearVisitedLinks();
                forkJoinPoolList.forEach(ForkJoinPool::shutdownNow);
                threadList.forEach(Thread::interrupt);
                siteRepository.findAll().forEach(site -> {
                    if (site.getStatus().equals(StatusType.INDEXING) || site.getStatus().equals(StatusType.FAILED)) {
                        site.setLastError("Индексация прервана пользователем");
                        saveSiteStatus(site, StatusType.FAILED);
                    }
                });
                threadList.clear();
                forkJoinPoolList.clear();
            });
            return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
        } else {
            return new IndexResponse(new JSONObject().put(RESULT, false)
                    .put(ERROR, "Индексация не запущена"), HttpStatus.BAD_REQUEST);
        }
    }

    @SneakyThrows
    public Response indexPage(String url) {
        if (!WebParser.isValidLink(url)) {
            return new IndexResponse(new JSONObject()
                    .put("Данная страница находится за пределами сайтов, " +
                            "указанных в конфигурационном файле", false), HttpStatus.BAD_REQUEST);
        }
        Site site = siteRepository.findSiteByUrl(url);
        if (site == null) site = createNewSite(url);
        assert site != null;
        if (site.getStatus() == StatusType.INDEXING) {
            return new IndexResponse(new JSONObject()
                    .put(ERROR, "Индексация уже запущена"), HttpStatus.BAD_REQUEST);
        }
        saveSiteStatus(site, StatusType.INDEXING);
        WebParser webParser = newWebParse(site);
        Page page = webParser.addPage(url).getKey();
        Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(page.getContent());
        for (Map.Entry<String, Integer> entry : lemmaRankMap.entrySet()) {
            String lemmaString = entry.getKey();
            int rank = entry.getValue();
            Lemma lemma = updateOrCreateLemma(site, lemmaString);
            Index index = indexRepository.findByLemmaAndPage(lemma, page);
            if (index == null) index = new Index(lemma, page, rank);
            else index.setRank(rank);
            indexRepository.save(index);
        }
        saveSiteStatus(site, StatusType.INDEXED);
        return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
    }

    private Site createNewSite(String url) {
        for (searchengine.config.Site initSite : initSiteList.getSites()) {
            if (url.replaceAll(HTTP_S_WWW, "").contains(initSite.getUrl().replaceAll(HTTP_S_WWW, ""))) {
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

    public void proceedPagesFromRedis(int batchSize) {
        long startIndex = 0;
        long numPages = Optional.ofNullable(redisTemplatePage.opsForList().size(WebParser.REDIS_KEY_PAGES)).orElse(0L);
        while (startIndex < numPages) {
            long endIndex = Math.min(startIndex + batchSize - 1, numPages - 1);
            List<Page> pageList = redisTemplatePage.opsForList().range(WebParser.REDIS_KEY_PAGES, startIndex, endIndex);
            try {
                assert pageList != null;
                pageRepository.saveAll(pageList);
            } catch (NullPointerException e) {
                logger.error(e.getMessage());
                break;
            }
            startIndex += batchSize;
        }
        redisTemplatePage.delete(WebParser.REDIS_KEY_PAGES);
    }

    public void proceedLemmasFromRedis(int batchSize) {
        Set<String> lemmaKeys = redisTemplateLemma.keys("*:*");
        assert lemmaKeys != null;
        int numLemmas = lemmaKeys.size();
        int startIndex = 0;
        while (startIndex < numLemmas) {
            int endIndex = Math.min(startIndex + batchSize - 1, numLemmas - 1);
            List<Lemma> lemmaList = new ArrayList<>();
            for (String key : new ArrayList<>(lemmaKeys).subList(startIndex, endIndex + 1)) {
                Lemma lemma = redisTemplateLemma.opsForValue().get(key);
                lemmaList.add(lemma);
            }
            lemmaRepository.saveAll(lemmaList);
            startIndex += batchSize;
        }
        redisTemplateLemma.delete(lemmaKeys);
    }

    public void proceedIndicesFromRedis(int batchSize) {
        long startIndex = 0;
        long numIndices = Optional.ofNullable(redisTemplateIndex.opsForList().size(WebParser.REDIS_KEY_INDICES)).orElse(0L);
        while (startIndex < numIndices) {
            long endIndex = Math.min(startIndex + batchSize - 1, numIndices - 1);
            List<Index> indexList = redisTemplateIndex.opsForList().range(WebParser.REDIS_KEY_INDICES, startIndex, endIndex);
            assert indexList != null;
            indexRepository.saveAll(indexList);
            startIndex += batchSize;
        }
        redisTemplateIndex.delete(WebParser.REDIS_KEY_INDICES);
    }

    private WebParser newWebParse(Site site) {
        WebParser webParser = new WebParser(initSiteList, pageRepository, lemmaRepository, indexRepository, config,
                lemmatisator, Collections.singletonList(site.getUrl()), redisTemplatePage, redisTemplateLemma, redisTemplateIndex);
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
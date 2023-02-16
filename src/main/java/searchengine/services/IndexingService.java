package searchengine.services;

import lombok.SneakyThrows;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
    @Autowired
    private RedisConnectionFactory connectionFactory;
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
    private final Object lock = new Object();

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
        registerShutdownHook();
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
        if (indexing.compareAndSet(false, true)) {
            if (!WebParser.isValidLink(url)) {
                return new IndexResponse(new JSONObject().put("Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле", false), HttpStatus.BAD_REQUEST);
            }
            searchengine.config.Site initSite = new searchengine.config.Site();
            for (searchengine.config.Site site : initSiteList.getSites()) {
                if (url.replaceAll(HTTP_S_WWW, "")
                        .contains(site.getUrl().replaceAll(HTTP_S_WWW, ""))) {
                    initSite = site;
                    break;
                }
            }
            Site site = siteRepository.findSiteByUrl(initSite.getUrl());
            if (site == null) {
                site = new Site(initSite.getUrl(), initSite.getName());
                siteRepository.save(site);
            }
            saveSiteStatus(site, StatusType.INDEXING);
            WebParser webParser = newWebParse(site);
            Map.Entry<Page, Boolean> indexedPage = webParser.addPage(url);
            Page page = indexedPage.getKey();
            extractLemmasAndSave(page, indexedPage.getValue(), initSiteList.getBatchsize());
            saveSiteStatus(site, StatusType.INDEXED);
            return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
        } else {
            return new IndexResponse(new JSONObject().put(RESULT, false)
                    .put(ERROR, "Индексация уже запущена"), HttpStatus.BAD_REQUEST);
        }
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
        int numLemmas = lemmaKeys.size();
        int startIndex = 0;
        while (startIndex < numLemmas) {
            int endIndex = Math.min(startIndex + batchSize - 1, numLemmas - 1);
            List<Lemma> lemmaList = new ArrayList<>();
            for (String key : new ArrayList<>(lemmaKeys).subList(startIndex, endIndex + 1)) {
                Lemma lemma = redisTemplateLemma.opsForValue().get(key);
                lemmaList.add(lemma);
            }
            try {
                lemmaRepository.saveAll(lemmaList);
            } catch (NullPointerException e) {
                logger.error(e.getMessage());
                break;
            }
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
            try {
                indexRepository.saveAll(indexList);
            } catch (NullPointerException e) {
                logger.error(e.getMessage());
                break;
            }
            startIndex += batchSize;
        }
        redisTemplateIndex.delete(WebParser.REDIS_KEY_INDICES);
    }

    private void extractLemmasAndSave(Page page, boolean newPage, int batchSize) {
        Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(page.getContent());
        List<Lemma> lemmasToInsert = new ArrayList<>();
        List<Index> indicesToInsert = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : lemmaRankMap.entrySet()) {
            String lemmaString = entry.getKey();
            int rank = entry.getValue();
            Lemma lemma = new Lemma(page.getSite(), lemmaString);
            lemmasToInsert.add(lemma);
            Index index;
            if (newPage) {
                index = new Index(lemma, page, rank);
            } else {
                index = indexRepository.findByLemmaAndPage(lemma, page);
                index.setRank(rank);
            }
            indicesToInsert.add(index);
        }

        saveBatch(lemmasToInsert, batchSize / 20, (batch, count) -> {
            List<Lemma> lemmasBatch = new ArrayList<>(batch);
            try {
                lemmaRepository.saveAll(lemmasBatch);
            } catch (DataIntegrityViolationException ex) {
                synchronized (lock) {
                    for (Lemma lemma : lemmasBatch) {
                        Lemma existingLemma = lemmaRepository.findLemmaByLemmaStringAndSite(lemma.getLemmaString(), lemma.getSite());
                        if (existingLemma != null) {
                            for (Index index : indicesToInsert) {
                                if (index.getLemma().getLemmaString().equals(existingLemma.getLemmaString())) {
                                    existingLemma.setFrequency(existingLemma.getFrequency() + 1);
                                    lemmaRepository.save(existingLemma);
                                    index.setLemma(existingLemma);
                                    break;
                                }
                            }
                        } else {
                            lemmaRepository.save(lemma);
                        }
                    }
                }
            }
        });

        saveBatch(indicesToInsert, batchSize, (batch, count) -> {
            List<Index> indicesBatch = new ArrayList<>(batch);
            try {
                indexRepository.saveAll(indicesBatch);
            } catch (DataIntegrityViolationException ex) {
                ex.printStackTrace();
            }
        });
    }

//    private static void deletePagesDuplicates() {
//        Set<Page> set = new HashSet<>();
//        List<Page> pagesWithoutDuplicates = new LinkedList<>();
//        for (Page page : WebParser.getPages()) {     //time complexity O(n), space complexity O(n)
//            if (!set.contains(page)) {
//                set.add(page);
//                pagesWithoutDuplicates.add(page);
//            }
//        }
//        WebParser.setPages(pagesWithoutDuplicates);
//        int s = pages.size();
//        for(int i = 0; i < s - 1; i++) {
//            for(int j = s - 1; j > i; j--) {
//                if(pages.get(i).equals(pages.get(j))) {
//                    pages.remove(j);
//                    s = pages.size();
//                }
//            }
//        }             time complexity O(n^2), space complexity O(1)
//    }


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

    private <T> void saveBatch(List<T> items, int batchSize, ObjIntConsumer<List<T>> saveFunction) {
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, endIndex);
            saveFunction.accept(batch, endIndex - i);
        }
    }

    private void saveSiteStatus(Site site, StatusType status) {
        site.setStatus(status);
        siteRepository.save(site);
    }

    public void flushDb() {
        RedisConnection connection = connectionFactory.getConnection();
        try {
            connection.flushDb();
        } finally {
            connection.close();
        }
    }
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::flushDb));
    }
}
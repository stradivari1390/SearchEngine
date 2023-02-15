package searchengine.services;

import lombok.SneakyThrows;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.dto.parsing.Lemmatisator;
import searchengine.model.*;
import searchengine.repository.*;
import searchengine.dto.parsing.WebParser;
import searchengine.responses.IndexResponse;
import searchengine.responses.Response;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final RedisTemplate<String, Page> redisTemplate;
    private final Config config;
    private static AtomicBoolean indexing = new AtomicBoolean(false);
    private List<ForkJoinPool> forkJoinPoolList;
    private List<Thread> threadList;
    private final Lemmatisator lemmatisator;
    private static final HtmlCleaner cleaner = new HtmlCleaner();

    @Autowired
    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository,
                           LemmaRepository lemmaRepository, IndexRepository indexRepository,
                           InitSiteList initSiteList, RedisTemplate<String, Page> redisTemplate,
                           Config config, Lemmatisator lemmatisator) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.initSiteList = initSiteList;
        this.redisTemplate = redisTemplate;
        this.config = config;
        this.lemmatisator = lemmatisator;
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
            WebParser.initiateValidationPatterns(initSiteList);
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
        List<WebParser> webParserList = new ArrayList<>();
        List<searchengine.config.Site> initSites = initSiteList.getSites();
        for (searchengine.config.Site initSite : initSites) {
            Site site = siteRepository.findSiteByUrl(initSite.getUrl());
            if (site == null) {
                site = new Site(initSite.getUrl(), initSite.getName());
            }
            site.setStatus(StatusType.INDEXING);
            webParserList.add(newWebParse(site));
            siteRepository.save(site);
        }
        webParserList.forEach(webParser -> threadList.add(new Thread(() -> {
            Site site = webParser.getSite();
            try {
                site.setStatus(StatusType.INDEXING);
                siteRepository.save(site);
                ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
                forkJoinPoolList.add(forkJoinPool);
                forkJoinPool.execute(webParser);
                int count = webParser.join();
                logger.info(webParser.getSite().getName() + ": " + count + " pages processed.");
                site.setStatus(StatusType.INDEXED);
                latch.countDown();
                siteRepository.save(site);
            } catch (CancellationException e) {
                e.printStackTrace();
                site.setLastError("Ошибка индексации: " + e.getMessage());
                site.setStatus(StatusType.FAILED);
                siteRepository.save(site);
            }
        })));
        threadList.forEach(Thread::start);
        try {
            latch.await();
            proceedPagesFromRedis(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    public Response stopIndexing() {
        if (indexing.compareAndSet(true, false)) {
            CompletableFuture.runAsync(() -> {
                WebParser.stopCrawling();
                proceedPagesFromRedis(1000);
                forkJoinPoolList.forEach(ForkJoinPool::shutdownNow);
                threadList.forEach(Thread::interrupt);
                siteRepository.findAll().forEach(site -> {
                    if (site.getStatus().equals(StatusType.INDEXING)) {
                        site.setLastError("Остановка индексации");
                        site.setStatus(StatusType.FAILED);
                        siteRepository.save(site);
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
            WebParser.initiateValidationPatterns(initSiteList);
            if (!WebParser.isValidLink(url)) {
                return new IndexResponse(new JSONObject().put("Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле", false), HttpStatus.BAD_REQUEST);
            }
            searchengine.config.Site initSite = new searchengine.config.Site();
            for (searchengine.config.Site site : initSiteList.getSites()) {
                if (url.replaceAll(HTTP_S_WWW, "")
                        .contains(site.getUrl().replaceAll(HTTP_S_WWW, ""))) {
                    initSite = site;
                }
            }
            Site site = siteRepository.findSiteByUrl(initSite.getUrl());
            if (site == null) {
                site = new Site(initSite.getUrl(), initSite.getName());
                siteRepository.save(site);
            }
            site.setStatus(StatusType.INDEXING);
            siteRepository.save(site);
            WebParser webParser = newWebParse(site);
            Map.Entry<Page, Boolean> indexedPage = webParser.addPage(url);
            Page page = indexedPage.getKey();
            extractLemmasAndSave(page, indexedPage.getValue(), 1000);
            site.setStatus(StatusType.INDEXED);
            siteRepository.save(site);
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
        long numPages = redisTemplate.opsForList().size(WebParser.REDIS_KEY);
        while (startIndex < numPages) {
            long endIndex = Math.min(startIndex + batchSize - 1, numPages - 1);
            List<Page> pageList = redisTemplate.opsForList().range(WebParser.REDIS_KEY, startIndex, endIndex);
            pageRepository.saveAll(pageList);
            pageList.forEach(p -> extractLemmasAndSave(p, true, 1000));
            startIndex += batchSize;
        }
        redisTemplate.delete(WebParser.REDIS_KEY);
    }


    private void extractLemmasAndSave(Page page, boolean newPage, int batchSize) {
        TagNode node = cleaner.clean(page.getContent());
        String plainText = node.getText().toString();
        Map<String, Integer> lemmaRankMap = lemmatisator.collectLemmasAndRanks(plainText);
        List<Lemma> lemmasToInsert = new ArrayList<>();
        List<Index> indicesToInsert = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : lemmaRankMap.entrySet()) {
            String lemmaString = entry.getKey();
            int rank = entry.getValue();
            Lemma lemma = lemmaRepository.findLemmaByLemmaStringAndSite(lemmaString, page.getSite());
            if (lemma == null) {
                lemma = new Lemma(page.getSite(), lemmaString);
                lemmasToInsert.add(lemma);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
            }
            Index index;
            if(newPage) {
                index = new Index(lemma, page, rank);
            } else {
                index = indexRepository.findByLemmaAndPage(lemma, page);
                index.setRank(rank);
            }
            indicesToInsert.add(index);
        }
        for (int i = 0; i < lemmasToInsert.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, lemmasToInsert.size() - 1);
            List<Lemma> lemmasBatch = lemmasToInsert.subList(i, endIndex);
            lemmaRepository.saveAll(lemmasBatch);
        }
        for (int i = 0; i < indicesToInsert.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, indicesToInsert.size() - 1);
            List<Index> indicesBatch = indicesToInsert.subList(i, endIndex);
            indexRepository.saveAll(indicesBatch);
        }
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
                lemmatisator, Collections.singletonList(site.getUrl()), redisTemplate);
        webParser.setSite(site);
        return webParser;
    }
}
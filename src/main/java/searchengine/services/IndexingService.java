package searchengine.services;

import lombok.SneakyThrows;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.exceptions.EmptyLinkException;
import searchengine.model.*;
import searchengine.repository.*;
import searchengine.dto.WebParser;
import searchengine.responses.ErrorResponse;
import searchengine.responses.IndexResponse;
import searchengine.responses.Response;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingService {
    private static final String RESULT = "result";
    private static final String ERROR = "error";
    private static final String HTTP_S_WWW = "^(http(s)?://(www\\.)?)";
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final InitSiteList initSiteList;
    private final Config config;
    private static AtomicBoolean indexing = new AtomicBoolean(false);
    private static List<ForkJoinPool> forkJoinPoolList = new ArrayList<>();
    private static List<Thread> threadList = new ArrayList<>();

    @Autowired
    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository,
                           LemmaRepository lemmaRepository, IndexRepository indexRepository,
                           InitSiteList initSiteList, Config config) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.initSiteList = initSiteList;
        this.config = config;
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
            clearData();
            List<searchengine.config.Site> siteList = initSiteList.getSites();
            WebParser.setInitSiteList(initSiteList);
            WebParser.initiateValidationPatterns();

            int numSites = siteList.size();
            CountDownLatch latch = new CountDownLatch(numSites);

            for (searchengine.config.Site initSite : siteList) {
                Site site = new Site(initSite.getUrl(), initSite.getName());
                site.setStatus(StatusType.INDEXING);
                siteRepository.save(site);

                CompletableFuture.runAsync(() -> new Thread(() -> {
                    threadList.add(Thread.currentThread());
                    ForkJoinPool pool = new ForkJoinPool();
                    forkJoinPoolList.add(pool);
                    WebParser webParser = new WebParser(pageRepository, lemmaRepository, indexRepository,
                            config, new ArrayList<>(Collections.singleton(site.getUrl())));
                    webParser.setSite(site);
                    pool.execute(webParser);
                    try {
                        webParser.get();
                        latch.countDown();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt();
                    }
                }).start());
            }

            CompletableFuture.runAsync(() -> {
                try {
                    latch.await();
                    indexing.set(false);
                    WebParser.clearVisitedLinks();
                    processPagesInserting();
                    WebParser.collectLemmasAndIndices();
                    processLemmasInserting();
                    processIndicesInserting();
                    List<Site> sites = siteRepository.findAll();
                    sites.forEach(site -> {
                        if (site.getStatus().equals(StatusType.INDEXING)) {
                            site.setStatus(StatusType.INDEXED);
                        }
                    });
                    WebParser.clearPagesSet();
                    WebParser.clearLemmasList();
                    WebParser.clearIndicesSet();
                    threadList.clear();
                    forkJoinPoolList.clear();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            });

            return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
        } else {
            return new IndexResponse(new JSONObject().put(RESULT, false)
                    .put(ERROR, "Indexing already started"), HttpStatus.BAD_REQUEST);
        }
    }

    @SneakyThrows
    public Response stopIndexing() {
        if (indexing.compareAndSet(true, false)) {
            WebParser.stopCrawling();
            threadList.forEach(Thread::interrupt);
            forkJoinPoolList.forEach(ForkJoinPool::shutdownNow);
            threadList.clear();
            forkJoinPoolList.clear();
            WebParser.clearVisitedLinks();
            processPagesInserting();
            WebParser.collectLemmasAndIndices();
            processLemmasInserting();
            processIndicesInserting();
            List<Site> sites = siteRepository.findAll();
            sites.forEach(site -> {
                if (site.getStatus().equals(StatusType.INDEXING)) {
                    site.setStatus(StatusType.FAILED);
                    site.setLastError("Indexing stopped by user");
                }
            });
            siteRepository.saveAll(sites);
            WebParser.clearPagesSet();
            WebParser.clearLemmasList();
            WebParser.clearIndicesSet();
            return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
        } else {
            return new IndexResponse(new JSONObject().put(RESULT, false)
                    .put(ERROR, "Indexing not started"), HttpStatus.BAD_REQUEST);
        }
    }

    @SneakyThrows
    public Response indexPage(String url) {
        if (url == null) {
            try {
                JSONObject response = new JSONObject();
                response.put(RESULT, false);
                response.put(ERROR, "Задана пустая ссылка");
                return new ErrorResponse(response, HttpStatus.BAD_REQUEST);
            } catch (JSONException e) {
                throw new EmptyLinkException(e);
            }
        }
        String siteName = getSiteName(url);
        String siteUrl = getSiteUrl(url);
        if(siteUrl == null) {
            return new IndexResponse(new JSONObject().put(RESULT, false), HttpStatus.BAD_REQUEST);
        }
        Site site = siteRepository.findByUrl(getSiteUrl(url));
        if (site == null) {
            site = new Site(siteUrl, siteName);
            siteRepository.save(site);
        }
        site.setStatus(StatusType.INDEXING);
        WebParser.setInitSiteList(initSiteList);
        WebParser.initiateValidationPatterns();
        WebParser webParser = new WebParser(pageRepository, lemmaRepository, indexRepository,
                config, new ArrayList<>(Collections.singleton(site.getUrl())));
        webParser.setSite(site);
        webParser.addPage(url);
        site.setStatus(StatusType.INDEXED);
        siteRepository.save(site);
        return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
    }

    private String getSiteUrl(String url) {
        return initSiteList.getSites().stream()
                .filter(s -> url.replaceAll(HTTP_S_WWW, "")
                        .contains(s.getUrl().replaceAll(HTTP_S_WWW, "")))
                .findFirst()
                .map(searchengine.config.Site::getUrl)
                .orElse(null);
    }

    private String getSiteName(String url) {
        return initSiteList.getSites().stream()
                .filter(s -> url.replaceAll(HTTP_S_WWW, "")
                        .contains(s.getUrl().replaceAll(HTTP_S_WWW, "")))
                .findFirst()
                .map(searchengine.config.Site::getName)
                .orElse(null);
    }

    public static boolean isIndexing() {
        return indexing.get();
    }

    private void processPagesInserting() {
        if (!WebParser.getPages().isEmpty()) {
            Set<Page> pages = WebParser.getPages();
            pageRepository.saveAll(pages);
        }
    }

    private void processLemmasInserting() {
        if (!WebParser.getLemmas().isEmpty()) {
            Set<Lemma> lemmas = WebParser.getLemmas();
            lemmaRepository.saveAll(lemmas);
        }
    }

    private void processIndicesInserting() {
        if (!WebParser.getIndices().isEmpty()) {
            Set<Index> indices = WebParser.getIndices();
            indexRepository.saveAll(indices);
        }
    }
}
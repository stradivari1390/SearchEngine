package searchengine.services;

import lombok.SneakyThrows;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, Page> redisTemplate;
    private final Config config;
    private static AtomicBoolean indexing = new AtomicBoolean(false);
    private static List<ForkJoinPool> forkJoinPoolList = new ArrayList<>();
    private static List<Thread> threadList = new ArrayList<>();

    @Autowired
    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository,
                           LemmaRepository lemmaRepository, IndexRepository indexRepository,
                           InitSiteList initSiteList, RedisTemplate<String, Page> redisTemplate, Config config) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.initSiteList = initSiteList;
        this.redisTemplate = redisTemplate;
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

            for (searchengine.config.Site initSite : siteList) {
                Site site = new Site(initSite.getUrl(), initSite.getName());
                site.setStatus(StatusType.INDEXING);
                siteRepository.save(site);

                threadList.add(new Thread(() -> {
                    ForkJoinPool pool = new ForkJoinPool();
                    forkJoinPoolList.add(pool);
                    WebParser webParser = new WebParser(pageRepository, lemmaRepository, indexRepository,
                            config, new ArrayList<>(Collections.singleton(site.getUrl())), redisTemplate);
                    webParser.setSite(site);
                    pool.submit(webParser);
                    try {
                        webParser.get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt();
                    }
                    WebParser.setPages(webParser.getPagesFromRedis());
                    processPagesInserting();
                    WebParser.collectLemmasAndIndices();
                    processLemmasInserting();
                    processIndicesInserting();
                    site.setStatus(StatusType.INDEXED);
                    WebParser.clearPagesList();
                    WebParser.clearLemmasList();
                    WebParser.clearIndicesList();
                    WebParser.clearVisitedLinks();
                }));
            }

            for (Thread thread : threadList) {
                if(!indexing.get()) {
                    break;
                }
                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }

            threadList.clear();
            forkJoinPoolList.clear();
            indexing.set(false);
            return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);  //ToDO: Response before threads end
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
            WebParser.clearPagesList();
            WebParser.clearLemmasList();
            WebParser.clearIndicesList();
            WebParser.clearVisitedLinks();
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
                config, new ArrayList<>(Collections.singleton(site.getUrl())), redisTemplate);
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
            deletePagesDuplicates();
            List<Page> pages = WebParser.getPages();
            pageRepository.saveAll(pages);
    }

    private static void deletePagesDuplicates() {
        Set<Page> set = new HashSet<>();
        List<Page> pagesWithoutDuplicates = new LinkedList<>();
        for (Page page : WebParser.getPages()) {
            if (!set.contains(page)) {
                set.add(page);
                pagesWithoutDuplicates.add(page);
            }
        }
        WebParser.setPages(pagesWithoutDuplicates);     //time complexity O(n), space complexity O(n)
//        int s = pages.size();
//        for(int i = 0; i < s - 1; i++) {
//            for(int j = s - 1; j > i; j--) {
//                if(pages.get(i).equals(pages.get(j))) {
//                    pages.remove(j);
//                    s = pages.size();
//                }
//            }
//        }             time complexity O(n^2), space complexity O(1)
    }

    private void processLemmasInserting() {
            List<Lemma> lemmas = WebParser.getLemmas();
            lemmaRepository.saveAll(lemmas);
    }

    private void processIndicesInserting() {
            List<Index> indices = WebParser.getIndices();
            indexRepository.saveAll(indices);
    }
}
package searchengine.services;

import lombok.SneakyThrows;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.dto.Lemmatisator;
import searchengine.exceptions.EmptyLinkException;
import searchengine.model.Site;
import searchengine.model.StatusType;
import searchengine.repository.*;
import searchengine.dto.WebParser;
import searchengine.responses.ErrorResponse;
import searchengine.responses.IndexResponse;
import searchengine.responses.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final Lemmatisator lemmatisator;
    private final Config config;
    private static AtomicBoolean indexing = new AtomicBoolean(false);

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

                new Thread(() -> {
                    ForkJoinPool pool = new ForkJoinPool();
                    WebParser webParser = new WebParser(pageRepository, lemmaRepository, indexRepository,
                            config, lemmatisator, new ArrayList<>(Collections.singleton(site.getUrl())));
                    webParser.setSite(site);
                    pool.execute(webParser);
                    try {
                        webParser.get();
                        site.setStatus(StatusType.INDEXED);
                        siteRepository.save(site);
                        latch.countDown();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
            try {
                latch.await();
                indexing.set(false);
                WebParser.clearVisitedLinks();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
            List<Site> sites = siteRepository.findAll();
            sites.forEach(site -> {
                if (site.getStatus().equals(StatusType.INDEXING)) {
                    site.setStatus(StatusType.FAILED);
                    site.setLastError("Indexing stopped by user");
                }
            });
            siteRepository.saveAll(sites);
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
        Site site = siteRepository.findByUrl(getSiteUrl(url));
        if (site == null) {
            site = new Site(getSiteUrl(url), getSiteName(url));
            siteRepository.save(site);
        }
        site.setStatus(StatusType.INDEXING);
        WebParser.setInitSiteList(initSiteList);
        WebParser.initiateValidationPatterns();
        WebParser webParser = new WebParser(pageRepository, lemmaRepository, indexRepository,
                config, lemmatisator, new ArrayList<>(Collections.singleton(site.getUrl())));
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
}
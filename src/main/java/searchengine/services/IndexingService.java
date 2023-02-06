package searchengine.services;

import lombok.SneakyThrows;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.dto.WebParsersStorage;
import searchengine.exceptions.EmptyLinkException;
import searchengine.model.Site;
import searchengine.model.StatusType;
import searchengine.repository.*;
import searchengine.dto.WebParser;
import searchengine.responses.ErrorResponse;
import searchengine.responses.IndexResponse;
import searchengine.responses.Response;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private List<WebParser> webParserList = new ArrayList<>();
    private Thread startIndexingThread;
    static AtomicBoolean isIndexing = new AtomicBoolean(false);

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
        if (isIndexing.get()) {
            return new IndexResponse(new JSONObject().put(RESULT, false)
                    .put(ERROR, "Indexing already started"), HttpStatus.BAD_REQUEST);
        }
        clearData();
        List<searchengine.config.Site> siteList = initSiteList.getSites();
        WebParser.setInitSiteList(initSiteList);
        WebParser.initiateValidationPatterns();
        for (searchengine.config.Site initSite : siteList) {
            Site site = new Site(initSite.getUrl(), initSite.getName());
            site.setStatus(StatusType.INDEXING);
            siteRepository.save(site);
            WebParser webParser = new WebParser(new HashSet<>(), pageRepository,
                    lemmaRepository, indexRepository, config);
            webParser.setSite(site, initSite.getUrl());
            webParserList.add(webParser);
        }
        isIndexing.set(true);
        startIndexingThread = new Thread(() -> {
            webParserList.forEach(webParser -> {
                Thread thread = new Thread(() -> {
                    ForkJoinPool forkJoinPool = new ForkJoinPool();
                    forkJoinPool.execute(webParser);
                    webParser.join();
                    Site site = webParser.getSite();
                    site.setStatus(StatusType.INDEXED);
                    siteRepository.save(site);
                    forkJoinPool.shutdown();
                });
                thread.start();
            });
            isIndexing.set(false);
        });
        startIndexingThread.start();
        return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
    }

    @SneakyThrows
    public Response stopIndexing() {
        if (!isIndexing.get()) {
            return new IndexResponse(new JSONObject().put(RESULT, false)
                    .put(ERROR, "Indexing not started"), HttpStatus.BAD_REQUEST);
        }
        WebParsersStorage.getInstance().setTerminationInProcess(true);
        startIndexingThread.interrupt();
        webParserList.forEach(webParser -> webParser.cancel(true));
        webParserList.clear();
        WebParsersStorage.getInstance().terminateAll();
        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(StatusType.INDEXING)) {
                site.setStatus(StatusType.FAILED);
                site.setLastError("Indexing stopped by user");
                siteRepository.save(site);
            }
        });
        WebParsersStorage.getInstance().setTerminationInProcess(false);
        isIndexing.set(false);
        return new IndexResponse(new JSONObject().put(RESULT, true), HttpStatus.OK);
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
        WebParser webParser = new WebParser(new HashSet<>(), pageRepository,
                lemmaRepository, indexRepository, config);
        webParser.setSite(site, url);
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
}
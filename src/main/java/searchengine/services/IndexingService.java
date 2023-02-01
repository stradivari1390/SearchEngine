package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexingService {

    private static final Logger logger = LogManager.getLogger(IndexingService.class);
    private SiteRepository siteRepository;
    private PageRepository pageRepository;
    private LemmaRepository lemmaRepository;
    private IndexRepository indexRepository;
    private InitSiteList initSiteList;
    private Config config;

    private List<Thread> threadList;
    private List<ForkJoinPool> forkJoinPoolList;
    private List<WebParser> webParserList;
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

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(StatusType.INDEXING)) {
                isIndexing.set(true);
            }
        });
        if (isIndexing.get()) {
            return new IndexResponse("startIndexing", true);
        }

        threadList = new ArrayList<>();
        forkJoinPoolList = new ArrayList<>();
        webParserList = new ArrayList<>();

        clearData();

        List<searchengine.config.Site> siteList = initSiteList.getSites();

        for (searchengine.config.Site initSite : siteList) {

            String initSiteUrl = initSite.getUrl();

            Site site = new Site(initSiteUrl, initSite.getName());
            site.setStatus(StatusType.INDEXING);
            siteRepository.save(site);
            WebParser webParser = new WebParser(new HashSet<>(), initSiteList, pageRepository,
                    lemmaRepository, indexRepository, config);
            webParser.setSite(site, initSiteUrl);
            webParserList.add(webParser);
        }
        isIndexing.set(true);
        for (WebParser webParser : webParserList) {

            Site site = webParser.getSite();

            Thread thread = new Thread(() -> {
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                forkJoinPoolList.add(forkJoinPool);
                forkJoinPool.execute(webParser);
                webParser.join();
                site.setStatus(StatusType.INDEXED);
                siteRepository.save(site);
            });

            threadList.add(thread);
        }

        startIndexingThread = new Thread(() -> {
            threadList.forEach(Thread::start);
            for (Thread thread : threadList) {
                try {
                    logger.info("Thread state: {}", thread.getState());
                    logger.info(thread.isAlive());
                    thread.join();
                } catch (InterruptedException e) {
                    logger.log(Level.WARN, "Interrupted!", e);
                    Thread.currentThread().interrupt();
                }
            }
            isIndexing.set(false);
        });
        startIndexingThread.start();
        return new IndexResponse("startIndexing", false);
    }

    @SneakyThrows
    public Response stopIndexing() {
        if (!isIndexing.get()) {
            return new IndexResponse("stopIndexing", false);
        }
        WebParsersStorage.getInstance().isTerminationInProcess().set(true);
        startIndexingThread.interrupt();
        Iterator<Thread> iterator = threadList.iterator();
        while (iterator.hasNext()) {
            Thread thread = iterator.next();
            thread.interrupt();
            iterator.remove();
        }
        for (ForkJoinPool forkJoinPool : forkJoinPoolList) {
            forkJoinPool.shutdown();
            try {
                forkJoinPool.awaitTermination(3000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.log(Level.WARN, "Interrupted!", e);
                Thread.currentThread().interrupt();
            }
            if (!forkJoinPool.isTerminated()) {
                forkJoinPool.shutdownNow();
            }
        }
        WebParsersStorage.getInstance().terminateAll();
        isIndexing.set(false);
        threadList.clear();
        forkJoinPoolList.clear();
        webParserList.clear();
        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(StatusType.INDEXING)) {
                site.setStatus(StatusType.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
            }
        });
        WebParsersStorage.getInstance().isTerminationInProcess().set(false);
        return new IndexResponse("stopIndexing", true);
    }

    @SneakyThrows
    public Response indexPage(String url) {
        if (url == null) {
            try {
                JSONObject response = new JSONObject();
                response.put("result", false);
                response.put("error", "Задана пустая ссылка");
                return new ErrorResponse(response, HttpStatus.BAD_REQUEST);
            } catch (JSONException e) {
                throw new EmptyLinkException(e);
            }
        }
        List<searchengine.config.Site> siteList = initSiteList.getSites();

        for (searchengine.config.Site initSite : siteList) {
            if (url.replaceAll("^(http(s)?://(www\\.)?)", "")
                    .contains(initSite.getUrl().replaceAll("^(http(s)?://(www\\.)?)", ""))) {

                Site site = siteRepository.findByUrl(initSite.getUrl());
                if (site == null) {
                    site = new Site(initSite.getUrl(), initSite.getName());
                }
                site.setStatus(StatusType.INDEXING);
                siteRepository.save(site);

                WebParser webParser = new WebParser(new HashSet<>(), initSiteList, pageRepository,
                        lemmaRepository, indexRepository, config);
                webParser.setSite(site, url);
                webParser.addPage(url);

                site.setStatus(StatusType.INDEXED);
                siteRepository.save(site);

                return new IndexResponse("indexPage", true);
            }
        }
        return new IndexResponse("indexPage", false);
    }
}
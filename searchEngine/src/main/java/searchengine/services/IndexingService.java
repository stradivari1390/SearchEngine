package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.dto.WebParsersStorage;
import searchengine.model.Site;
import searchengine.model.StatusType;
import searchengine.repository.*;
import searchengine.dto.Lemmatisator;
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
@RequiredArgsConstructor
public class IndexingService {

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private InitSiteList initSiteList;
    @Autowired
    private Config config;
    @Autowired
    Lemmatisator lemmatisator;

    private static List<Thread> threadList = new ArrayList<>();
    private Thread startIndexingThread;
    private static List<ForkJoinPool> forkJoinPoolList = new ArrayList<>();
    private static List<WebParser> webParserList = new ArrayList<>();
    static AtomicBoolean isIndexing = new AtomicBoolean(false);

    private void clearData() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    public Response startIndexing() {

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(StatusType.INDEXING)) {
                isIndexing.set(true);
            }
        });
        if (isIndexing.get()) {
            return new IndexResponse("startIndexing", true);
        }

        clearData();

        List<searchengine.config.Site> siteList = initSiteList.getSites();

        for (searchengine.config.Site initSite : siteList) {

            String initSiteUrl = initSite.getUrl();

            Site site = new Site(initSiteUrl, initSite.getName());
            site.setStatus(StatusType.INDEXING);
            siteRepository.save(site);

            webParserList.add(new WebParser(initSiteUrl, site, initSiteList, pageRepository,
                    lemmaRepository, indexRepository, config, lemmatisator, new HashSet<>()));
        }
        isIndexing.set(true);
        for (WebParser webParser : webParserList) {

            Site site = webParser.getSite();

            Thread thread = new Thread(() -> {
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                forkJoinPoolList.add(forkJoinPool);
                forkJoinPool.invoke(webParser);

                int count = webParser.join();

                site.setStatus(StatusType.INDEXED);
                siteRepository.save(site);

                System.out.println("Site " + site.getName() + " indexed, page-count - " + count);
            });

            threadList.add(thread);
        }

        startIndexingThread = new Thread(() -> {
            threadList.forEach(Thread::start);
            try {
                for (Thread thread : threadList) {
                    thread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            isIndexing.set(false);
        });
        startIndexingThread.start();
        return new IndexResponse("startIndexing", false);
    }

    public Response stopIndexing() {
        if (!isIndexing.get()) {
            return new IndexResponse("stopIndexing", false);
        }
        for (Thread thread : threadList) {
            thread.interrupt();
        }
        for (ForkJoinPool forkJoinPool : forkJoinPoolList) {
            forkJoinPool.shutdown();
        }
        WebParsersStorage.getInstance().terminateAll();
        isIndexing.set(false);
        threadList.clear();
        forkJoinPoolList.clear();
        webParserList.clear();
        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(StatusType.INDEXING)) {
                site.setStatus(StatusType.FAILED);
                siteRepository.save(site);
            }
        });
        return new IndexResponse("stopIndexing", true);
    }

    public Response indexPage(String url) {
        if (url == null) {
            try {
                JSONObject response = new JSONObject();
                response.put("result", false);
                response.put("error", "Задана пустая ссылка");
                return new ErrorResponse(response, HttpStatus.BAD_REQUEST);
            } catch (JSONException e) {
                throw new RuntimeException(e);
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

                WebParser webParser = new WebParser(url, site, initSiteList, pageRepository, lemmaRepository, indexRepository,
                        config, lemmatisator, new HashSet<>());

                webParser.addPage(url);

                site.setStatus(StatusType.INDEXED);
                siteRepository.save(site);

                return new IndexResponse("indexPage", true);
            }
        }
        return new IndexResponse("indexPage", false);
    }
}
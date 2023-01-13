package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Config;
import searchengine.config.InitSiteList;
import searchengine.model.Site;
import searchengine.model.StatusType;
import searchengine.repository.*;
import searchengine.services.lemmatisationService.Lemmatisator;
import searchengine.services.websiteCrawlingService.SitemapNode;
import searchengine.services.websiteCrawlingService.WebParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
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

    private List<Thread> threadList = new ArrayList<>();
    private List<ForkJoinPool> forkJoinPoolList = new ArrayList<>();

    private void clearData() {

        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    public void indexing() {

        threadList = new ArrayList<>();

        forkJoinPoolList = new ArrayList<>();

        clearData();

        List<WebParser> webParserList = new ArrayList<>();
        List<searchengine.config.Site> siteList = initSiteList.getSites();

        for (searchengine.config.Site initSite : siteList) {

            String initSiteUrl = initSite.getUrl();

            Site site = new Site(initSiteUrl, initSite.getName());
            site.setStatus(StatusType.INDEXING);
            siteRepository.save(site);

            SitemapNode sitemapRoot = new SitemapNode(initSiteUrl);

            webParserList.add(new WebParser(site, sitemapRoot,
                    siteRepository, pageRepository, lemmaRepository, indexRepository,
                    config, lemmatisator));
        }

        webParserList.forEach(webParser -> threadList.add(new Thread(() -> {

            Site site = webParser.getSite();

            try {

                ForkJoinPool forkJoinPool = new ForkJoinPool();  //ToDo add parallelism?
                forkJoinPoolList.add(forkJoinPool);
                forkJoinPool.execute(webParser);

                int count = webParser.join();

                site.setStatus(StatusType.INDEXED);
                siteRepository.save(site);

                System.out.println("Site " + site.getName() + " indexed, page-count - " + count);

            } catch (CancellationException ex) {
                ex.printStackTrace();
                site.setLastError("Indexing error: " + ex.getMessage());
                site.setStatus(StatusType.FAILED);
                siteRepository.save(site);
            }
        })));

        threadList.forEach(Thread::start);

        forkJoinPoolList.forEach(ForkJoinPool::shutdown);
    }

    public boolean startIndexing() {

        AtomicBoolean isIndexing = new AtomicBoolean();

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(StatusType.INDEXING)) {
                isIndexing.set(true);
            }
        });

        if (isIndexing.get()) return true;

        new Thread(this::indexing).start();

        return false;
    }

    public boolean stopIndexing() {

        System.out.println("Threads count: " + threadList.size());

        AtomicBoolean isIndexing = new AtomicBoolean(false);

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(StatusType.INDEXING)) {
                isIndexing.set(true);
            }
        });

        if (!isIndexing.get()) {
            return false;
        }

        forkJoinPoolList.forEach(ForkJoinPool::shutdownNow);        //ToDo: finalize, doesnt work
        threadList.forEach(Thread::interrupt);                      //ToDO: figure out, is order important?

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(StatusType.INDEXING)) {
                site.setLastError("Индексация остановлена пользователем");
                site.setStatus(StatusType.FAILED);
                siteRepository.save(site);
            }
        });

        forkJoinPoolList.clear();
        threadList.clear();

        return true;
    }

    public boolean indexPage(String url) {

        List<searchengine.config.Site> siteList = initSiteList.getSites();

        for (searchengine.config.Site initSite : siteList) {
            if (url.contains(initSite.getUrl()) ||
                    url.contains(initSite.getUrl().replace("/www.", "/"))) {

                Site site = siteRepository.findByUrl(initSite.getUrl());
                if (site == null) {
                    site = new Site(initSite.getUrl(), initSite.getName());
                }
                site.setStatus(StatusType.INDEXING);
                siteRepository.save(site);

                WebParser webParser = new WebParser(site, new SitemapNode(url),
                        siteRepository, pageRepository, lemmaRepository, indexRepository,
                        config, lemmatisator);
                try {
                    webParser.addPage();
                } catch (IOException e) {
                    return false;
                }

                site.setStatus(StatusType.INDEXED);
                siteRepository.save(site);

                return true;
            }
        }
        return false;
    }
}

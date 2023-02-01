package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.InitSiteList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.responses.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.StatusType;
import searchengine.repository.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private InitSiteList initSiteList;

    @Autowired
    public StatisticsServiceImpl(SiteRepository siteRepository, PageRepository pageRepository,
                                 LemmaRepository lemmaRepository, InitSiteList initSiteList) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.initSiteList = initSiteList;
    }

    @Override
    public StatisticsResponse getStatistics() {

        StatisticsData statistic = new StatisticsData();
        AtomicInteger allLemmas = new AtomicInteger();
        AtomicInteger allPages = new AtomicInteger();
        AtomicInteger allSites = new AtomicInteger();
        List<Site> siteList = siteRepository.findAll();

        if (siteList.isEmpty()) {
            for (searchengine.config.Site initSite : initSiteList.getSites()) {

                Site site = new Site(initSite.getUrl(), initSite.getName());
                site.setStatus(StatusType.FAILED);
                siteRepository.save(site);
                siteList.add(site);
            }
        }

        siteList.forEach(site -> {
            int pages = pageRepository.countBySite(site);
            int lemmas = lemmaRepository.countBySite(site);
            statistic.addDetailed(new DetailedStatisticsItem(site, pages, lemmas));
            allPages.updateAndGet(value -> value + pages);
            allLemmas.updateAndGet(value -> value + lemmas);
            allSites.getAndIncrement();
        });

        TotalStatistics total = new TotalStatistics(allSites.get(), allPages.get(),
                allLemmas.get(), IndexingService.isIndexing.get());
        statistic.setTotal(total);
        return new StatisticsResponse(true, statistic, HttpStatus.OK);
    }
}
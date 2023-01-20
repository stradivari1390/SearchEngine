package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.InitSiteList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.StatusType;
import searchengine.repository.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private InitSiteList initSiteList;

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
//            List<searchengine.config.Site> sites = initSiteList.getSites();
//            TotalStatistics total = new TotalStatistics(sites.size(), 0, 0, false);
//            List<DetailedStatisticsItem> detailed = new ArrayList<>();
//            for (searchengine.config.Site site : sites) {
//                DetailedStatisticsItem item = new DetailedStatisticsItem();
//                item.setName(site.getName());
//                item.setUrl(site.getUrl());
//                item.setPages(0);
//                item.setLemmas(0);
//                item.setStatus("Not indexed");
//                item.setError("");
//                item.setStatusTime(System.currentTimeMillis());
//                detailed.add(item);
//            }
//            StatisticsData data = new StatisticsData();
//            data.setTotal(total);
//            data.setDetailed(detailed);
//            return new StatisticsResponse(true, data);
        }
        AtomicBoolean isIndexing = new AtomicBoolean();
        siteList.forEach(site -> {
            if(site.getStatus() == StatusType.INDEXING) isIndexing.set(true);
            int pages = pageRepository.countBySite(site);
            int lemmas = lemmaRepository.countBySite(site);
            statistic.addDetailed(new DetailedStatisticsItem(site, pages, lemmas));
            allPages.updateAndGet(value -> value + pages);
            allLemmas.updateAndGet(value -> value + lemmas);
            allSites.getAndIncrement();
        });

        TotalStatistics total = new TotalStatistics(allSites.get(), allPages.get(), allLemmas.get(), isIndexing.get());
        statistic.setTotal(total);
        return new StatisticsResponse(true, statistic);
    }
}
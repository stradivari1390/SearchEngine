package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repository.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;

    @Override
    public StatisticsResponse getStatistics() {
        StatisticsData statistic = new StatisticsData();

        AtomicInteger allLemmas = new AtomicInteger();
        AtomicInteger allPages = new AtomicInteger();
        AtomicInteger allSites = new AtomicInteger();

        List<Site> siteList = siteRepository.findAll();

        if (siteList.size() == 0) {
            return new StatisticsResponse();
        }

        siteList.forEach(site -> {

            int pages = pageRepository.countBySite(site);
            int lemmas = lemmaRepository.countBySite(site);

            statistic.addDetailed(new DetailedStatisticsItem(site, pages, lemmas));

            allPages.updateAndGet(value -> value + pages);
            allLemmas.updateAndGet(value -> value + lemmas);
            allSites.getAndIncrement();
        });

        TotalStatistics total = new TotalStatistics();
        total.setIndexing(true);
        total.setLemmas(allLemmas.get());
        total.setPages(allPages.get());
        total.setSites(allSites.get());

        statistic.setTotal(total);

        StatisticsResponse statistics = new StatisticsResponse();

        statistics.setResult(true);
        statistics.setStatisticsData(statistic);

        return statistics;
    }
}
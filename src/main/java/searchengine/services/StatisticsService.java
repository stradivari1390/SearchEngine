package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.Statistics;
import searchengine.dto.responses.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repository.*;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatisticsService {
    private final SiteRepository siteRepository;

    @Autowired
    public StatisticsService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics(0, 0, 0, false);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        List<Site> sites = siteRepository.findAll();
        for (Site site : sites) {
            int pages = site.getPages().size();
            int lemmas = site.getLemmas().size();

            DetailedStatisticsItem detailedStatisticsDto = new DetailedStatisticsItem(
                    site.getUrl(),
                    site.getName(),
                    site.getStatus().toString(),
                    site.getStatusTime().getTime(),
                    site.getLastError(),
                    pages,
                    lemmas
            );

            detailed.add(detailedStatisticsDto);

            total.setSites(total.getSites() + 1);
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            total.setIndexing(IndexingService.isIndexing());
        }
        return new StatisticsResponse(true, new Statistics(total, detailed));
    }
}
package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import searchengine.model.Site;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailedStatisticsItem {

    private String url;
    private String name;
    private String status;
    private long statusTime;
    private String error;
    private int pages;
    private int lemmas;
    private Site site;

    public DetailedStatisticsItem(Site site, int pages, int lemmas) {
        this.site = site;
        url = site.getUrl();
        name = site.getName();
        status = site.getStatus().toString();
        statusTime = site.getStatusTime().getTime();
        error = site.getLastError();
        this.pages = pages;
        this.lemmas = lemmas;
    }
}
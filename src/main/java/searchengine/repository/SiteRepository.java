package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {
    Site findSiteByUrl(String url);

    @Query("SELECT p.site FROM Page p WHERE p = :page")
    Site findSiteByPage(Page page);
}
package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import searchengine.model.Site;
import searchengine.model.StatusType;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {
    Site findSiteByUrl(String url);

    List<Site> findAllByStatus(StatusType statusType);

    @Modifying
    @Query("UPDATE Site s SET s.status = :status, s.lastError = :lastError WHERE s.status = :oldStatus")
    int updateSiteStatus(@Param("status") StatusType status, @Param("lastError") String lastError, @Param("oldStatus") StatusType oldStatus);
}
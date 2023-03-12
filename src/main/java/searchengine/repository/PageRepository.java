package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findAllBySite(Site site);

    @Query("SELECT p FROM Page p " +
            "WHERE EXISTS " +
            "(SELECT 1 FROM Index i " +
            "JOIN i.lemma l " +
            "WHERE i.page = p " +
            "AND l IN :lemmas " +
            "GROUP BY i.page " +
            "HAVING COUNT(l) = :lemmaCount)")
    List<Page> findAllByLemmas(@Param("lemmas") List<Lemma> lemmas, @Param("lemmaCount") long lemmaCount);

    Page findByPath(String path);

    Long countBySite(Site site);
}
package searchengine.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @EntityGraph(attributePaths = {"site"})
    Lemma findLemmaByLemmaStringAndSite(String lemmaString, Site site);

    @Query("SELECT l FROM Lemma l JOIN FETCH l.indices WHERE l.lemmaString = :lemmaString")
    List<Lemma> findAllByLemmaStringFetchIndices(@Param("lemmaString") String lemmaString);

    List<Lemma> findAllByLemmaString(String lemmaString);

    List<Lemma> findAllBySite(Site site);

    Long countBySite(Site site);
}
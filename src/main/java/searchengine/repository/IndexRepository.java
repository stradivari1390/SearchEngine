package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {
    Index findByLemmaAndPage(Lemma lemma, Page page);

    List<Index> findAllByLemma(Lemma lemma);

    Long countDistinctByLemmaId(Long lemmaId);
}
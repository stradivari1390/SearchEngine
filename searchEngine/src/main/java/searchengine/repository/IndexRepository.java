package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    Index findByLemmaAndPage(Lemma lemma, Page page);
}
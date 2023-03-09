package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findAllBySite(Site site);

    @Query("SELECT DISTINCT i.page FROM Index i WHERE i IN :indices")
    List<Page> findAllByIndices(@Param("indices") List<Index> indices);

    Integer countBySite(Site site);

    Page findByPath(String path);
}
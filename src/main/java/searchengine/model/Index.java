package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.Objects;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "`index`")
public class Index {

    public Index(Lemma lemma, Page page, float rank) {
        this.lemma = lemma;
        this.page = page;
        this.rank = rank;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id", nullable = false)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE, optional = false)
    @JoinColumn(name = "lemma_id", nullable = false, foreignKey = @ForeignKey(name = "FK_index_lemma"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Lemma lemma;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE, optional = false)
    @JoinColumn(name = "page_id", nullable = false, foreignKey = @ForeignKey(name = "FK_index_page"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Page page;

    @Column(name = "ranks", nullable = false)
    private float rank;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Index index = (Index) o;
        return id != 0 && Objects.equals(id, index.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id = " + id + ", " +
                "rank = " + rank + ")";
    }
}
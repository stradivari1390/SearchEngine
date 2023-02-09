package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(uniqueConstraints={
        @UniqueConstraint(columnNames = {"lemma", "site_id"})
})
public class Lemma implements Comparable<Lemma>{

    public Lemma(Site site, String lemmaString) {
        this.site = site;
        this.lemmaString = lemmaString;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE, optional = false)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "FK_lemma_site"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;

    @Column(name = "lemma", nullable = false, columnDefinition = "VARCHAR(255)")
    private String lemmaString;

    @Column(name = "frequency", nullable = false)
    private int frequency = 1;

    @OneToMany(mappedBy = "lemma", fetch = FetchType.LAZY)
    private Set<Index> indices = new HashSet<>();

    @Override
    public int compareTo(Lemma l) {
        if (frequency > l.getFrequency()) {
            return 1;
        } else if (frequency < l.getFrequency()) {
            return -1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Lemma lemma = (Lemma) o;
        return id != 0 && Objects.equals(id, lemma.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id = " + id + ", " +
                "lemmaString = " + lemmaString + ", " +
                "frequency = " + frequency + ")";
    }
}

package searchengine.model;

import lombok.*;

import javax.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
@Entity
public class Lemma implements Comparable<Lemma>{

    public Lemma(Site site, String lemma) {
        this.site = site;
        this.lemma = lemma;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ToString.Include
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ToString.Include
    @Column(name = "lemma", nullable = false, columnDefinition = "VARCHAR(255)")
    private String lemma;

    @ToString.Include
    @Column(name = "frequency", nullable = false) //, columnDefinition = "integer default 1")  ToDo: figure out why doesnt work
    private int frequency = 1;

    @Override
    public int compareTo(Lemma o) {
        if (frequency > o.getFrequency()) {
            return 1;
        } else if (frequency < o.getFrequency()) {
            return -1;
        }
        return 0;
    }
}

package searchengine.model;

import lombok.*;

import javax.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Entity
public class Site {

    public Site(String url, String name) {
        this.url = url;
        this.name = name;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')")
    private StatusType status;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "status_time", nullable = false)
    private Date statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @ToString.Include
    @Column(name = "url", nullable = false, columnDefinition = "VARCHAR(255)")
    private String url;

    @ToString.Include
    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(255)")
    private String name;

    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY)
    private Set<Page> pages = new HashSet<>();

    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY)
    private Set<Lemma> lemmas = new HashSet<>();

    public void setStatus(StatusType status) {
        this.status = status;
        this.setStatusTime(new Date());
    }
}

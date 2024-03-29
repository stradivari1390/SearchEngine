package searchengine.model;

import lombok.*;

import javax.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "site")
public class Site {

    public Site(String url, String name) {
        this.url = url;
        this.name = name;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "site_id_seq")
    @SequenceGenerator(name = "site_id_seq", sequenceName = "site_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "status", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private StatusType status;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "status_time", nullable = false)
    private Date statusTime;

    @Column(name = "last_error", columnDefinition = "VARCHAR(255)")
    private String lastError;

    @Column(name = "url", nullable = false, columnDefinition = "VARCHAR(100)")
    private String url;

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(50)")
    private String name;

    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY)
    private Set<Page> pages = new HashSet<>();

    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY)
    private Set<Lemma> lemmas = new HashSet<>();

    public void setStatus(StatusType status) {
        this.status = status;
        this.setStatusTime(new Date());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Site)) return false;
        Site site = (Site) o;
        return getUrl().equals(site.getUrl()) && getName().equals(site.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUrl(), getName());
    }

    @Override
    public String toString() {
        return "Site{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
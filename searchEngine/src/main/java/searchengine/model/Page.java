package searchengine.model;

import lombok.*;

import javax.persistence.Index;
import javax.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "Page", indexes = {
        @Index(name = "idx_page_path_unq", columnList = "path", unique = true)
})
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ToString.Include
    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @ToString.Include
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

}

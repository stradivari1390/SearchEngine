package searchengine.model;

import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.Index;
import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

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

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE, optional = false)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "FK_page_site"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;

    @ToString.Include
    @Column(name = "path", nullable = false, columnDefinition = "VARCHAR(255)")
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @ToString.Include
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @OneToMany(mappedBy = "page", fetch = FetchType.LAZY)
    private Set<searchengine.model.Index> indices = new HashSet<>();
}
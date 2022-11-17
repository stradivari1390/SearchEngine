package searchengine.model;

import lombok.*;

import javax.persistence.*;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Entity
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')")
    private StatusType status;

    @Temporal(TemporalType.DATE)
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

}

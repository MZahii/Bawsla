package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.RaisonReputation;

/**
 * Mouvement de points (historique consultable) ; coursId sert au classement hebdomadaire par cours.
 */
@Entity
@Table(name = "evenement_reputation",
        indexes = {
                @Index(name = "ix_evenement_reputation_user", columnList = "userId"),
                @Index(name = "ix_evenement_reputation_cours", columnList = "coursId, creeLe")})
public class EvenementReputation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private RaisonReputation raison;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private CibleType cibleType;

    private Long cibleId;

    private Long coursId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getDelta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }

    public RaisonReputation getRaison() { return raison; }
    public void setRaison(RaisonReputation raison) { this.raison = raison; }

    public CibleType getCibleType() { return cibleType; }
    public void setCibleType(CibleType cibleType) { this.cibleType = cibleType; }

    public Long getCibleId() { return cibleId; }
    public void setCibleId(Long cibleId) { this.cibleId = cibleId; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

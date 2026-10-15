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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;

/**
 * Historique NON MODIFIABLE de toutes les décisions de modération (qui, quoi, quand, pourquoi).
 * cibleType est libre (DISCUSSION, REPONSE, COMMENTAIRE, UTILISATEUR, ETIQUETTE, SIGNALEMENT, PARAMETRES…).
 */
@Entity
@Immutable
@Table(name = "journal_moderation",
        indexes = {
                @Index(name = "ix_journal_moderation_acteur", columnList = "acteurId"),
                @Index(name = "ix_journal_moderation_date", columnList = "creeLe")})
public class JournalModeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long acteurId;

    @Column(nullable = false, length = 20, updatable = false)
    private String role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, updatable = false)
    private ActionModeration action;

    @Column(nullable = false, length = 30, updatable = false)
    private String cibleType;

    @Column(updatable = false)
    private Long cibleId;

    @Column(updatable = false)
    private Long coursId;

    @Column(length = 500, updatable = false)
    private String motif;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getActeurId() { return acteurId; }
    public void setActeurId(Long acteurId) { this.acteurId = acteurId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public ActionModeration getAction() { return action; }
    public void setAction(ActionModeration action) { this.action = action; }

    public String getCibleType() { return cibleType; }
    public void setCibleType(String cibleType) { this.cibleType = cibleType; }

    public Long getCibleId() { return cibleId; }
    public void setCibleId(Long cibleId) { this.cibleId = cibleId; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

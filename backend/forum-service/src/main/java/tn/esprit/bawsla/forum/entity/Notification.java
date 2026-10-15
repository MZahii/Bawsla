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
import tn.esprit.bawsla.forum.entity.enums.TypeNotification;

/**
 * Notification interne au forum (la cloche du haut et la page Notifications).
 */
@Entity
@Table(name = "notification",
        indexes = {
                @Index(name = "ix_notification_destinataire", columnList = "destinataireId, lue")})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long destinataireId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TypeNotification type;

    private Long discussionId;

    private Long reponseId;

    /** Null pour La Vigie ou le système. */
    private Long acteurId;

    @Column(length = 200)
    private String extrait;

    @Column(nullable = false)
    private boolean lue;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getDestinataireId() { return destinataireId; }
    public void setDestinataireId(Long destinataireId) { this.destinataireId = destinataireId; }

    public TypeNotification getType() { return type; }
    public void setType(TypeNotification type) { this.type = type; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public Long getReponseId() { return reponseId; }
    public void setReponseId(Long reponseId) { this.reponseId = reponseId; }

    public Long getActeurId() { return acteurId; }
    public void setActeurId(Long acteurId) { this.acteurId = acteurId; }

    public String getExtrait() { return extrait; }
    public void setExtrait(String extrait) { this.extrait = extrait; }

    public boolean isLue() { return lue; }
    public void setLue(boolean lue) { this.lue = lue; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

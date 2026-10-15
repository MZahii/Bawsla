package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Conversation privée « Demander au cours » entre un apprenant et La Vigie (jamais publiée telle quelle).
 */
@Entity
@Table(name = "conversation_vigie",
        indexes = {
                @Index(name = "ix_conversation_vigie_user", columnList = "userId")})
public class ConversationVigie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long coursId;

    @Column(length = 200)
    private String titre;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @Column(nullable = false)
    private LocalDateTime derniereActiviteLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
        if (derniereActiviteLe == null) {
            derniereActiviteLe = creeLe;
        }
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }

    public LocalDateTime getDerniereActiviteLe() { return derniereActiviteLe; }
    public void setDerniereActiviteLe(LocalDateTime derniereActiviteLe) { this.derniereActiviteLe = derniereActiviteLe; }
}

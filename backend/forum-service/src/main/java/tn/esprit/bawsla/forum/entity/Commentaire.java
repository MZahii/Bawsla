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
 * Commentaire court (600 caractères au plus), sous une discussion OU sous une réponse, un seul niveau.
 */
@Entity
@Table(name = "commentaire",
        indexes = {
                @Index(name = "ix_commentaire_discussion", columnList = "discussionId"),
                @Index(name = "ix_commentaire_reponse", columnList = "reponseId")})
public class Commentaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Toujours renseigné (fil d'appartenance), même pour un commentaire de réponse. */
    @Column(nullable = false)
    private Long discussionId;

    /** Renseigné pour un commentaire de réponse, null pour un commentaire de la question. */
    private Long reponseId;

    @Column(nullable = false, updatable = false)
    private Long auteurId;

    @Column(nullable = false, length = 600)
    private String contenu;

    @Column(nullable = false)
    private boolean masque;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    private LocalDateTime modifieLe;

    private LocalDateTime supprimeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public Long getReponseId() { return reponseId; }
    public void setReponseId(Long reponseId) { this.reponseId = reponseId; }

    public Long getAuteurId() { return auteurId; }
    public void setAuteurId(Long auteurId) { this.auteurId = auteurId; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public boolean isMasque() { return masque; }
    public void setMasque(boolean masque) { this.masque = masque; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }

    public LocalDateTime getModifieLe() { return modifieLe; }
    public void setModifieLe(LocalDateTime modifieLe) { this.modifieLe = modifieLe; }

    public LocalDateTime getSupprimeLe() { return supprimeLe; }
    public void setSupprimeLe(LocalDateTime supprimeLe) { this.supprimeLe = supprimeLe; }
}

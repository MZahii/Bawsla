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
 * Réponse humaine à une discussion. Tri à l'affichage : acceptée, approuvée par le formateur, votes, date.
 */
@Entity
@Table(name = "reponse",
        indexes = {
                @Index(name = "ix_reponse_discussion", columnList = "discussionId"),
                @Index(name = "ix_reponse_auteur", columnList = "auteurId")})
public class Reponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long discussionId;

    @Column(nullable = false, updatable = false)
    private Long auteurId;

    @Column(nullable = false)
    private boolean anonyme;

    /** Markdown. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private boolean acceptee;

    /** Formateur du cours (ou admin) qui a approuvé la réponse. */
    private Long approuveeParId;

    private LocalDateTime approuveeLe;

    @Column(nullable = false)
    private boolean masquee;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    private LocalDateTime modifieLe;

    private LocalDateTime supprimeeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public Long getAuteurId() { return auteurId; }
    public void setAuteurId(Long auteurId) { this.auteurId = auteurId; }

    public boolean isAnonyme() { return anonyme; }
    public void setAnonyme(boolean anonyme) { this.anonyme = anonyme; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public boolean isAcceptee() { return acceptee; }
    public void setAcceptee(boolean acceptee) { this.acceptee = acceptee; }

    public Long getApprouveeParId() { return approuveeParId; }
    public void setApprouveeParId(Long approuveeParId) { this.approuveeParId = approuveeParId; }

    public LocalDateTime getApprouveeLe() { return approuveeLe; }
    public void setApprouveeLe(LocalDateTime approuveeLe) { this.approuveeLe = approuveeLe; }

    public boolean isMasquee() { return masquee; }
    public void setMasquee(boolean masquee) { this.masquee = masquee; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }

    public LocalDateTime getModifieLe() { return modifieLe; }
    public void setModifieLe(LocalDateTime modifieLe) { this.modifieLe = modifieLe; }

    public LocalDateTime getSupprimeeLe() { return supprimeeLe; }
    public void setSupprimeeLe(LocalDateTime supprimeeLe) { this.supprimeeLe = supprimeeLe; }
}

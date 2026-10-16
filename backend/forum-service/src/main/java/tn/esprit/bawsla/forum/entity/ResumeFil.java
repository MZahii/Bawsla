package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Résumé « En bref » d'un fil long (3 à 5 puces, une par ligne), régénéré quand le fil grandit.
 */
@Entity
@Table(name = "resume_fil")
public class ResumeFil {

    @Id
    private Long discussionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @Column(nullable = false)
    private int nbMessagesAuMoment;

    @Column(length = 20)
    private String versionPrompt;

    @Column(nullable = false)
    private LocalDateTime creeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public int getNbMessagesAuMoment() { return nbMessagesAuMoment; }
    public void setNbMessagesAuMoment(int nbMessagesAuMoment) { this.nbMessagesAuMoment = nbMessagesAuMoment; }

    public String getVersionPrompt() { return versionPrompt; }
    public void setVersionPrompt(String versionPrompt) { this.versionPrompt = versionPrompt; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

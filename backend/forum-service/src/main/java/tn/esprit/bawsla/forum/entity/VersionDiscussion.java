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
 * Version précédente d'une discussion, conservée à chaque édition (historique consultable).
 */
@Entity
@Table(name = "version_discussion",
        indexes = {
                @Index(name = "ix_version_discussion_discussion", columnList = "discussionId")})
public class VersionDiscussion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long discussionId;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Column(nullable = false)
    private LocalDateTime modifieLe;

    @Column(nullable = false)
    private Long modifieParId;

    @PrePersist
    void onCreate() {
        if (modifieLe == null) {
            modifieLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getModifieLe() { return modifieLe; }
    public void setModifieLe(LocalDateTime modifieLe) { this.modifieLe = modifieLe; }

    public Long getModifieParId() { return modifieParId; }
    public void setModifieParId(Long modifieParId) { this.modifieParId = modifieParId; }
}

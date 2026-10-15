package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Passage tiré d'une réponse VALIDÉE par un humain (acceptée, approuvée, ou réponse de La Vigie validée /
 * corrigée) : deuxième source de La Vigie. Index FULLTEXT(texte) créé au démarrage.
 */
@Entity
@Table(name = "passage_forum",
        indexes = {
                @Index(name = "ix_passage_forum_cours", columnList = "coursId"),
                @Index(name = "ix_passage_forum_discussion", columnList = "discussionId")})
public class PassageForum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false)
    private Long discussionId;

    private Long reponseId;

    private Long reponseIaId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @Convert(converter = VecteurConverter.class)
    @Column(columnDefinition = "BLOB")
    private float[] embedding;

    @Column(length = 80)
    private String modeleEmbedding;

    @Column(nullable = false)
    private LocalDateTime valideLe;

    @PrePersist
    void onCreate() {
        if (valideLe == null) {
            valideLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public Long getReponseId() { return reponseId; }
    public void setReponseId(Long reponseId) { this.reponseId = reponseId; }

    public Long getReponseIaId() { return reponseIaId; }
    public void setReponseIaId(Long reponseIaId) { this.reponseIaId = reponseIaId; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public String getModeleEmbedding() { return modeleEmbedding; }
    public void setModeleEmbedding(String modeleEmbedding) { this.modeleEmbedding = modeleEmbedding; }

    public LocalDateTime getValideLe() { return valideLe; }
    public void setValideLe(LocalDateTime valideLe) { this.valideLe = valideLe; }
}

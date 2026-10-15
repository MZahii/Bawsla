package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Image jointe (PNG, JPG, WebP, 2 Mo au plus, octets magiques vérifiés). Le fichier est stocké sur disque
 * sous un nom aléatoire (nomStockage), jamais sous le nom envoyé par l'utilisateur.
 */
@Entity
@Table(name = "piece_jointe")
public class PieceJointe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long discussionId;

    private Long reponseId;

    @Column(nullable = false)
    private Long auteurId;

    @Column(nullable = false, length = 100, unique = true)
    private String nomStockage;

    /** Type MIME vérifié. */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private long taille;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

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

    public String getNomStockage() { return nomStockage; }
    public void setNomStockage(String nomStockage) { this.nomStockage = nomStockage; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getTaille() { return taille; }
    public void setTaille(long taille) { this.taille = taille; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

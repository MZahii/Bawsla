package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Avis « Utile / Pas utile » d'un utilisateur sur une réponse de La Vigie (un seul par personne, modifiable).
 */
@Entity
@Table(name = "avis_vigie",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_avis_vigie_cible", columnNames = {"userId", "reponseIaId"})})
public class AvisVigie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long reponseIaId;

    @Column(nullable = false)
    private boolean utile;

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

    public Long getReponseIaId() { return reponseIaId; }
    public void setReponseIaId(Long reponseIaId) { this.reponseIaId = reponseIaId; }

    public boolean isUtile() { return utile; }
    public void setUtile(boolean utile) { this.utile = utile; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

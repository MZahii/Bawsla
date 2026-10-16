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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.CibleType;

/**
 * Vote +1 / −1 d'un utilisateur sur une discussion ou une réponse ; un seul par cible (annuler = supprimer).
 */
@Entity
@Table(name = "vote",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vote_cible", columnNames = {"userId", "cibleType", "cibleId"})},
        indexes = {
                @Index(name = "ix_vote_cible", columnList = "cibleType, cibleId")})
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private CibleType cibleType;

    @Column(nullable = false)
    private Long cibleId;

    @Column(nullable = false)
    private int valeur;

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

    public CibleType getCibleType() { return cibleType; }
    public void setCibleType(CibleType cibleType) { this.cibleType = cibleType; }

    public Long getCibleId() { return cibleId; }
    public void setCibleId(Long cibleId) { this.cibleId = cibleId; }

    public int getValeur() { return valeur; }
    public void setValeur(int valeur) { this.valeur = valeur; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

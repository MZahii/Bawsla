package tn.esprit.bawsla.forum.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;

/**
 * Points et niveau d'un utilisateur dans le forum (aucun autre module ne les gère).
 */
@Entity
@Table(name = "reputation")
public class Reputation {

    @Id
    private Long userId;

    @Column(nullable = false)
    private int points;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private NiveauReputation niveau = NiveauReputation.MOUSSE;

    /** Pour le badge Assidu (7 jours d'affilée). */
    @Column(nullable = false)
    private int joursActifsConsecutifs;

    private LocalDate dernierJourActif;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public NiveauReputation getNiveau() { return niveau; }
    public void setNiveau(NiveauReputation niveau) { this.niveau = niveau; }

    public int getJoursActifsConsecutifs() { return joursActifsConsecutifs; }
    public void setJoursActifsConsecutifs(int joursActifsConsecutifs) { this.joursActifsConsecutifs = joursActifsConsecutifs; }

    public LocalDate getDernierJourActif() { return dernierJourActif; }
    public void setDernierJourActif(LocalDate dernierJourActif) { this.dernierJourActif = dernierJourActif; }
}

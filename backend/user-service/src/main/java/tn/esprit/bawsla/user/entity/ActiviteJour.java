package tn.esprit.bawsla.user.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Un jour où l'utilisateur a été actif (au plus une ligne par jour) : sert à compter ses jours actifs sur 30 jours. */
@Entity
@Table(name = "activites_jour", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "jour"}))
public class ActiviteJour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate jour;

    protected ActiviteJour() {
    }

    public ActiviteJour(Long userId, LocalDate jour) {
        this.userId = userId;
        this.jour = jour;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getJour() { return jour; }
}

package tn.esprit.bawsla.cours.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Un chapitre terminé par un apprenant inscrit. Supprimé avec le chapitre, avec l'inscription ou quand l'apprenant l'annule. */
@Entity
@Table(name = "chapitre_termine",
        uniqueConstraints = @UniqueConstraint(name = "uk_chapitre_termine", columnNames = {"inscription_id", "chapitreId"}))
public class ChapitreTermine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscription_id", nullable = false)
    private Inscription inscription;

    @Column(nullable = false)
    private Long chapitreId;

    @Column(nullable = false)
    private LocalDateTime dateFin;

    protected ChapitreTermine() {
    }

    public ChapitreTermine(Inscription inscription, Long chapitreId) {
        this.inscription = inscription;
        this.chapitreId = chapitreId;
        this.dateFin = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Inscription getInscription() { return inscription; }
    public Long getChapitreId() { return chapitreId; }
    public LocalDateTime getDateFin() { return dateFin; }
}

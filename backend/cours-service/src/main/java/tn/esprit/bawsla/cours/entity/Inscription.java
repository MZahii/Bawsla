package tn.esprit.bawsla.cours.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Inscription d'un apprenant à un cours. On ne stocke que son identifiant ({@code etudiantId}) : aucune autre donnée personnelle.
 * Pas de clé étrangère vers user-service (autre base). {@code coursId} est une simple colonne : les inscriptions d'un cours sont
 * supprimées avec lui par {@code CoursService}.
 *
 * <p>La progression n'est pas stockée : elle est recalculée à chaque lecture (chapitres terminés ÷ chapitres existants du cours), car le
 * formateur peut ajouter ou supprimer des chapitres. {@code termine} est tenu à jour à chaque action de l'apprenant, mais les réponses
 * de l'API le recalculent.</p>
 */
@Entity
@Table(name = "inscription",
        uniqueConstraints = @UniqueConstraint(name = "uk_inscription_etudiant_cours", columnNames = {"etudiantId", "coursId"}),
        indexes = @Index(name = "idx_inscription_cours", columnList = "coursId"))
public class Inscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long etudiantId;

    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false)
    private LocalDateTime dateInscription;

    @Column(nullable = false)
    private boolean termine;

    /** Dernier chapitre marqué terminé. Null tant que l'apprenant n'a rien terminé. */
    private LocalDateTime dateDerniereActivite;

    /** Minutes passées dans le lecteur. NULL tant que le suivi du temps n'existe pas : le champ est alors omis de l'API. */
    private Integer tempsPasseMinutes;

    protected Inscription() {
    }

    public Inscription(Long etudiantId, Long coursId) {
        this.etudiantId = etudiantId;
        this.coursId = coursId;
        this.dateInscription = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getEtudiantId() { return etudiantId; }
    public Long getCoursId() { return coursId; }
    public LocalDateTime getDateInscription() { return dateInscription; }
    public boolean isTermine() { return termine; }
    public void setTermine(boolean termine) { this.termine = termine; }
    public LocalDateTime getDateDerniereActivite() { return dateDerniereActivite; }
    public void setDateDerniereActivite(LocalDateTime dateDerniereActivite) { this.dateDerniereActivite = dateDerniereActivite; }
    public Integer getTempsPasseMinutes() { return tempsPasseMinutes; }
}

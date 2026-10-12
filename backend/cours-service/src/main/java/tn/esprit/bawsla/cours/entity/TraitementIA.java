package tn.esprit.bawsla.cours.entity;

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

/**
 * Suivi d'une étape du traitement IA d'un cours. Une « exécution » (UUID) regroupe les étapes d'un même lancement :
 * l'interface les lit pour afficher la progression.
 */
@Entity
@Table(name = "traitement_ia", indexes = @Index(name = "idx_traitement_cours", columnList = "coursId"))
public class TraitementIA {

    public static final String PORTEE_FICHE = "FICHE";

    public static String porteeExplication(LangueExplication langue, ModeExplication mode) {
        return "EXPLICATION:" + langue.code() + ":" + mode.name();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false, length = 36)
    private String execution;

    /** Ce que le traitement produit : "FICHE" ou "EXPLICATION:fr:MONOLOGUE" (langue et mode). Plusieurs suivis par cours. */
    @Column(length = 40)
    private String portee = PORTEE_FICHE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtapeIA etape;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutTraitement statut = StatutTraitement.EN_ATTENTE;

    private LocalDateTime debut;

    private LocalDateTime fin;

    private Long dureeMs;

    /** Message lisible (jamais de clé, de prompt ni de trace technique). */
    @Column(length = 500)
    private String erreur;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    public static TraitementIA enAttente(Long coursId, String execution, EtapeIA etape) {
        return enAttente(coursId, execution, etape, PORTEE_FICHE);
    }

    public static TraitementIA enAttente(Long coursId, String execution, EtapeIA etape, String portee) {
        TraitementIA t = new TraitementIA();
        t.portee = portee;
        t.coursId = coursId;
        t.execution = execution;
        t.etape = etape;
        return t;
    }

    public Long getId() { return id; }
    public Long getCoursId() { return coursId; }
    public String getExecution() { return execution; }
    public String getPortee() { return portee; }
    public EtapeIA getEtape() { return etape; }

    public StatutTraitement getStatut() { return statut; }
    public void setStatut(StatutTraitement statut) { this.statut = statut; }

    public LocalDateTime getDebut() { return debut; }
    public void setDebut(LocalDateTime debut) { this.debut = debut; }

    public LocalDateTime getFin() { return fin; }
    public void setFin(LocalDateTime fin) { this.fin = fin; }

    public Long getDureeMs() { return dureeMs; }
    public void setDureeMs(Long dureeMs) { this.dureeMs = dureeMs; }

    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}

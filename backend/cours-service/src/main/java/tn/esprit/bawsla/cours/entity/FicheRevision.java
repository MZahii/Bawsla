package tn.esprit.bawsla.cours.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Fiche de révision d'un cours (résumé, mots-clés, notions) produite par l'IA puis relue par le formateur.
 * Un cours peut en avoir plusieurs : on garde l'historique. Les apprenants voient la plus récente VALIDEE.
 */
@Entity
@Table(name = "fiche_revision", indexes = @Index(name = "idx_fiche_cours", columnList = "coursId"))
public class FicheRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutFiche statut = StatutFiche.GENEREE;

    @Column(columnDefinition = "TEXT")
    private String resume;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fiche_mot_cle", joinColumns = @JoinColumn(name = "fiche_id"))
    @OrderColumn(name = "position")
    @Column(name = "mot_cle", length = 200)
    private List<String> motsCles = new ArrayList<>();

    @OneToMany(mappedBy = "fiche", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Notion> notions = new ArrayList<>();

    /** De 0 à 100, calculé sans LLM (voir /api/ai/cours/fidelite). */
    private Integer scoreFidelite;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fiche_alerte", joinColumns = @JoinColumn(name = "fiche_id"))
    @OrderColumn(name = "position")
    @Column(name = "alerte", length = 1000)
    private List<String> alertes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Niveau niveau;

    @Column(length = 20)
    private String versionPrompt;

    @Column(length = 100)
    private String modele;

    /** true dès que le formateur a corrigé la fiche à la main. */
    @Column(nullable = false)
    private boolean modifieeParFormateur;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateGeneration;

    private LocalDateTime dateValidation;

    @PrePersist
    void onCreate() {
        dateGeneration = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public StatutFiche getStatut() { return statut; }
    public void setStatut(StatutFiche statut) { this.statut = statut; }

    public String getResume() { return resume; }
    public void setResume(String resume) { this.resume = resume; }

    public List<String> getMotsCles() { return motsCles; }

    public List<Notion> getNotions() { return notions; }

    public Integer getScoreFidelite() { return scoreFidelite; }
    public void setScoreFidelite(Integer scoreFidelite) { this.scoreFidelite = scoreFidelite; }

    public List<String> getAlertes() { return alertes; }

    public Niveau getNiveau() { return niveau; }
    public void setNiveau(Niveau niveau) { this.niveau = niveau; }

    public String getVersionPrompt() { return versionPrompt; }
    public void setVersionPrompt(String versionPrompt) { this.versionPrompt = versionPrompt; }

    public String getModele() { return modele; }
    public void setModele(String modele) { this.modele = modele; }

    public boolean isModifieeParFormateur() { return modifieeParFormateur; }
    public void setModifieeParFormateur(boolean modifieeParFormateur) { this.modifieeParFormateur = modifieeParFormateur; }

    public LocalDateTime getDateGeneration() { return dateGeneration; }

    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }
}

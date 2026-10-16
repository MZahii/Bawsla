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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Résumé automatique LOCAL d'un cours (modèle Hugging Face sur CPU, ou phrases clés), sans clé API. Une ligne par génération :
 * l'historique est conservé, l'apprenant voit la dernière version VALIDE. Score et alertes sont réservés au formateur.
 */
@Entity
@Table(name = "resume_local", indexes = @Index(name = "idx_resume_local_cours", columnList = "coursId"))
public class ResumeLocal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    /** Langue détectée du cours : fr ou en. */
    @Column(nullable = false, length = 8)
    private String langue;

    /** Modèle utilisé (Hugging Face) ou "extractif-textrank". */
    @Column(length = 120)
    private String modele;

    /** abstractif (reformulé) ou extractif (phrases clés du cours). */
    @Column(nullable = false, length = 20)
    private String modeUtilise;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordre ASC")
    private List<PhraseResumeLocal> phrases = new ArrayList<>();

    private Integer scoreFidelite;

    /** Score du reformulé même remplacé par l'extractif (null : non tenté). */
    private Integer scoreReformule;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_local_alerte", joinColumns = @JoinColumn(name = "resume_id"))
    @Column(name = "alerte", length = 500)
    private List<String> alertes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutResumeLocal statut = StatutResumeLocal.GENERE;

    /** Nom UUID du fichier audio dans le dossier de stockage (voir StockageService), null s'il n'y en a pas. */
    @Column(length = 60)
    private String cheminAudio;

    @Column(length = 40)
    private String mime;

    private Long tailleAudio;

    @Column(length = 30)
    private String fournisseurAudio;

    /** Durée réelle de l'audio en millisecondes. */
    private Long dureeAudio;

    /** Temps de calcul du résumé côté IA (le premier chargement du modèle est long). */
    private Long dureeCalculMs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateValidation;

    @PrePersist
    void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    public boolean aUnAudio() {
        return cheminAudio != null;
    }

    public Long getId() { return id; }
    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }
    public String getLangue() { return langue; }
    public void setLangue(String langue) { this.langue = langue; }
    public String getModele() { return modele; }
    public void setModele(String modele) { this.modele = modele; }
    public String getModeUtilise() { return modeUtilise; }
    public void setModeUtilise(String modeUtilise) { this.modeUtilise = modeUtilise; }
    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }
    public List<PhraseResumeLocal> getPhrases() { return phrases; }
    public Integer getScoreFidelite() { return scoreFidelite; }
    public void setScoreFidelite(Integer scoreFidelite) { this.scoreFidelite = scoreFidelite; }
    public Integer getScoreReformule() { return scoreReformule; }
    public void setScoreReformule(Integer scoreReformule) { this.scoreReformule = scoreReformule; }
    public List<String> getAlertes() { return alertes; }
    public StatutResumeLocal getStatut() { return statut; }
    public void setStatut(StatutResumeLocal statut) { this.statut = statut; }
    public String getCheminAudio() { return cheminAudio; }
    public void setCheminAudio(String cheminAudio) { this.cheminAudio = cheminAudio; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Long getTailleAudio() { return tailleAudio; }
    public void setTailleAudio(Long tailleAudio) { this.tailleAudio = tailleAudio; }
    public String getFournisseurAudio() { return fournisseurAudio; }
    public void setFournisseurAudio(String fournisseurAudio) { this.fournisseurAudio = fournisseurAudio; }
    public Long getDureeAudio() { return dureeAudio; }
    public void setDureeAudio(Long dureeAudio) { this.dureeAudio = dureeAudio; }
    public Long getDureeCalculMs() { return dureeCalculMs; }
    public void setDureeCalculMs(Long dureeCalculMs) { this.dureeCalculMs = dureeCalculMs; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }
}

package tn.esprit.bawsla.cours.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
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
 * Explication orale d'un cours (script d'une vidéo, puis son audio) dans une langue et un mode donnés.
 * Un cours en a plusieurs : on garde l'historique. Les apprenants voient la plus récente VALIDEE de chaque
 * combinaison langue x mode. L'anglais est la traduction du français validé du même mode.
 *
 * <p>L'audio est lié au script par une empreinte : {@code empreinteScript} est celle du texte actuel,
 * {@code empreinteAudio} celle du texte qui a servi à produire l'audio. Elles diffèrent quand le formateur a modifié le
 * script après l'aperçu.</p>
 */
@Entity
@Table(name = "explication_audio", indexes = @Index(name = "idx_explication_cours", columnList = "coursId,langue,mode"))
public class ExplicationAudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private LangueExplication langue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModeExplication mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutFiche statut = StatutFiche.GENEREE;

    @OneToMany(mappedBy = "explication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<SegmentExplication> segments = new ArrayList<>();

    private Integer scoreFidelite;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "explication_alerte", joinColumns = @JoinColumn(name = "explication_id"))
    @OrderColumn(name = "position")
    @Column(name = "alerte", length = 1000)
    private List<String> alertes = new ArrayList<>();

    @Column(length = 30)
    private String versionPrompt;

    @Column(length = 100)
    private String modele;

    private Integer nbMots;

    private Integer dureeEstimeeSecondes;

    @Column(nullable = false)
    private boolean modifieeParFormateur;

    /** true : aucune fiche validée n'a fourni de plan, l'IA a choisi elle-même les notions. */
    @Column(nullable = false)
    private boolean notionsChoisiesParIa;

    /** true : le français a été validé de nouveau depuis cette traduction, qui est à refaire. */
    @Column(nullable = false)
    private boolean aRetraduire;

    /** Pour une traduction : identifiant du script français dont elle est tirée. */
    private Long sourceId;

    @Column(length = 64)
    private String empreinteScript;

    // ---- audio (aperçu, puis version diffusée)

    @Column(length = 60)
    private String audioFichier;

    @Column(length = 40)
    private String audioMime;

    @Column(length = 30)
    private String audioFournisseur;

    private Long audioTaille;

    private Long dureeMs;

    /** true : le minutage de chaque segment est réparti au prorata des caractères (un seul appel TTS pour tout le script). */
    @Column(nullable = false)
    private boolean minutageEstime;

    /** Empreinte du script au moment où l'audio a été produit. */
    @Column(length = 64)
    private String empreinteAudio;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateGeneration;

    private LocalDateTime dateValidation;

    private LocalDateTime dateAudio;

    @PrePersist
    void onCreate() {
        dateGeneration = LocalDateTime.now();
    }

    /** SHA-256 du script (langue, mode, et pour chaque segment : ordre, type, locuteur, texte). Les extraits ne comptent pas. */
    public String calculerEmpreinte() {
        StringBuilder sb = new StringBuilder(langue.code()).append('|').append(mode.code());
        for (SegmentExplication s : segments) {
            sb.append('\u001f').append(s.getOrdre()).append(':').append(s.getType().code()).append(':')
                    .append(s.getLocuteur().code()).append(':').append(s.getTexte() == null ? "" : s.getTexte().strip());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean aUnAudio() {
        return audioFichier != null;
    }

    /** L'audio correspond exactement au script actuel. */
    public boolean audioAJour() {
        return audioFichier != null && empreinteScript != null && empreinteScript.equals(empreinteAudio);
    }

    public Long getId() { return id; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public LangueExplication getLangue() { return langue; }
    public void setLangue(LangueExplication langue) { this.langue = langue; }

    public ModeExplication getMode() { return mode; }
    public void setMode(ModeExplication mode) { this.mode = mode; }

    public StatutFiche getStatut() { return statut; }
    public void setStatut(StatutFiche statut) { this.statut = statut; }

    public List<SegmentExplication> getSegments() { return segments; }

    public Integer getScoreFidelite() { return scoreFidelite; }
    public void setScoreFidelite(Integer scoreFidelite) { this.scoreFidelite = scoreFidelite; }

    public List<String> getAlertes() { return alertes; }

    public String getVersionPrompt() { return versionPrompt; }
    public void setVersionPrompt(String versionPrompt) { this.versionPrompt = versionPrompt; }

    public String getModele() { return modele; }
    public void setModele(String modele) { this.modele = modele; }

    public Integer getNbMots() { return nbMots; }
    public void setNbMots(Integer nbMots) { this.nbMots = nbMots; }

    public Integer getDureeEstimeeSecondes() { return dureeEstimeeSecondes; }
    public void setDureeEstimeeSecondes(Integer dureeEstimeeSecondes) { this.dureeEstimeeSecondes = dureeEstimeeSecondes; }

    public boolean isModifieeParFormateur() { return modifieeParFormateur; }
    public void setModifieeParFormateur(boolean modifieeParFormateur) { this.modifieeParFormateur = modifieeParFormateur; }

    public boolean isNotionsChoisiesParIa() { return notionsChoisiesParIa; }
    public void setNotionsChoisiesParIa(boolean notionsChoisiesParIa) { this.notionsChoisiesParIa = notionsChoisiesParIa; }

    public boolean isARetraduire() { return aRetraduire; }
    public void setARetraduire(boolean aRetraduire) { this.aRetraduire = aRetraduire; }

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }

    public String getEmpreinteScript() { return empreinteScript; }
    public void setEmpreinteScript(String empreinteScript) { this.empreinteScript = empreinteScript; }

    public String getAudioFichier() { return audioFichier; }
    public void setAudioFichier(String audioFichier) { this.audioFichier = audioFichier; }

    public String getAudioMime() { return audioMime; }
    public void setAudioMime(String audioMime) { this.audioMime = audioMime; }

    public String getAudioFournisseur() { return audioFournisseur; }
    public void setAudioFournisseur(String audioFournisseur) { this.audioFournisseur = audioFournisseur; }

    public Long getAudioTaille() { return audioTaille; }
    public void setAudioTaille(Long audioTaille) { this.audioTaille = audioTaille; }

    public Long getDureeMs() { return dureeMs; }
    public void setDureeMs(Long dureeMs) { this.dureeMs = dureeMs; }

    public boolean isMinutageEstime() { return minutageEstime; }
    public void setMinutageEstime(boolean minutageEstime) { this.minutageEstime = minutageEstime; }

    public String getEmpreinteAudio() { return empreinteAudio; }
    public void setEmpreinteAudio(String empreinteAudio) { this.empreinteAudio = empreinteAudio; }

    public LocalDateTime getDateGeneration() { return dateGeneration; }

    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }

    public LocalDateTime getDateAudio() { return dateAudio; }
    public void setDateAudio(LocalDateTime dateAudio) { this.dateAudio = dateAudio; }
}

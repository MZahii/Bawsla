package tn.esprit.bawsla.quiz.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Diagnostic d'une mauvaise réponse : créé à la correction (erreur associée au distracteur choisi), enrichi par la
 * remédiation IA (explication, conseil, question de rattrapage). La question de rattrapage est gardée côté serveur
 * pour corriger la réponse de l'apprenant sans lui envoyer la bonne réponse à l'avance.
 */
@Entity
@Table(name = "notion_ratee")
public class NotionRatee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reponse_id", unique = true)
    private Reponse reponse;

    @Column(nullable = false)
    private Long etudiantId;

    @Column(nullable = false)
    private Long coursId;

    /** Compétence visée (ou thème du quiz si la question n'en a pas). */
    @Column(nullable = false, length = 150)
    private String notion;

    @Column(columnDefinition = "TEXT")
    private String diagnostic;

    /** Raisonnement Chain-of-Thought de l'IA : jamais renvoyé à l'apprenant. */
    @Column(columnDefinition = "TEXT")
    private String raisonnement;

    @Column(columnDefinition = "TEXT")
    private String explicationIa;

    @Column(columnDefinition = "TEXT")
    private String conseil;

    /** Question de rattrapage générée (JSON, avec les bonnes réponses) ; null tant que l'IA n'a pas été appelée. */
    @Column(columnDefinition = "TEXT")
    private String rattrapageJson;

    @Column(length = 20)
    private String fournisseurIa;

    @Column(length = 40)
    private String versionPrompt;

    /** Vrai quand l'apprenant a réussi la question de rattrapage. */
    @Column(nullable = false)
    private boolean resolue;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    protected NotionRatee() {
    }

    public NotionRatee(Reponse reponse, Long etudiantId, Long coursId, String notion, String diagnostic) {
        this.reponse = reponse;
        this.etudiantId = etudiantId;
        this.coursId = coursId;
        this.notion = notion;
        this.diagnostic = diagnostic;
    }

    public Long getId() { return id; }

    public Reponse getReponse() { return reponse; }

    public Long getEtudiantId() { return etudiantId; }

    public Long getCoursId() { return coursId; }

    public String getNotion() { return notion; }

    public String getDiagnostic() { return diagnostic; }

    public String getRaisonnement() { return raisonnement; }
    public void setRaisonnement(String raisonnement) { this.raisonnement = raisonnement; }

    public String getExplicationIa() { return explicationIa; }
    public void setExplicationIa(String explicationIa) { this.explicationIa = explicationIa; }

    public String getConseil() { return conseil; }
    public void setConseil(String conseil) { this.conseil = conseil; }

    public String getRattrapageJson() { return rattrapageJson; }
    public void setRattrapageJson(String rattrapageJson) { this.rattrapageJson = rattrapageJson; }

    public String getFournisseurIa() { return fournisseurIa; }
    public void setFournisseurIa(String fournisseurIa) { this.fournisseurIa = fournisseurIa; }

    public String getVersionPrompt() { return versionPrompt; }
    public void setVersionPrompt(String versionPrompt) { this.versionPrompt = versionPrompt; }

    public boolean isResolue() { return resolue; }
    public void setResolue(boolean resolue) { this.resolue = resolue; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}

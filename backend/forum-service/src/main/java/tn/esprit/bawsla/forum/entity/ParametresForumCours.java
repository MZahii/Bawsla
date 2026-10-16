package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Réglages du forum d'UN cours, modifiables par le formateur du cours ou l'admin.
 * Absent = valeurs par défaut (forum ouvert, anonymat autorisé, La Vigie active, pas de mode examen).
 */
@Entity
@Table(name = "parametres_forum_cours")
public class ParametresForumCours {

    @Id
    private Long coursId;

    @Column(nullable = false)
    private boolean ouvert = true;

    @Column(nullable = false)
    private boolean anonymatAutorise = true;

    @Column(nullable = false)
    private boolean vigieActive = true;

    @Column(nullable = false)
    private boolean reponseGeneraleAutorisee = true;

    /** La Vigie ne donne que des indices, réponse générale refusée. */
    @Column(nullable = false)
    private boolean modeExamen;

    private LocalDateTime modeExamenFin;

    @Column(columnDefinition = "TEXT")
    private String questionModele;

    private Long modifiePar;

    private LocalDateTime dateModification;

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public boolean isOuvert() { return ouvert; }
    public void setOuvert(boolean ouvert) { this.ouvert = ouvert; }

    public boolean isAnonymatAutorise() { return anonymatAutorise; }
    public void setAnonymatAutorise(boolean anonymatAutorise) { this.anonymatAutorise = anonymatAutorise; }

    public boolean isVigieActive() { return vigieActive; }
    public void setVigieActive(boolean vigieActive) { this.vigieActive = vigieActive; }

    public boolean isReponseGeneraleAutorisee() { return reponseGeneraleAutorisee; }
    public void setReponseGeneraleAutorisee(boolean reponseGeneraleAutorisee) { this.reponseGeneraleAutorisee = reponseGeneraleAutorisee; }

    public boolean isModeExamen() { return modeExamen; }
    public void setModeExamen(boolean modeExamen) { this.modeExamen = modeExamen; }

    public LocalDateTime getModeExamenFin() { return modeExamenFin; }
    public void setModeExamenFin(LocalDateTime modeExamenFin) { this.modeExamenFin = modeExamenFin; }

    public String getQuestionModele() { return questionModele; }
    public void setQuestionModele(String questionModele) { this.questionModele = questionModele; }

    public Long getModifiePar() { return modifiePar; }
    public void setModifiePar(Long modifiePar) { this.modifiePar = modifiePar; }

    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) { this.dateModification = dateModification; }
}

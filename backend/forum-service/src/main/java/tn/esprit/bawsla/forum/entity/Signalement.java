package tn.esprit.bawsla.forum.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.CategorieModeration;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.MotifSignalement;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;

/**
 * Signalement d'un message par un utilisateur (auteurId renseigné) ou par l'IA de modération (auteurId null,
 * categorieIa et scoreIa renseignés). Un formateur peut l'escalader à l'administration.
 */
@Entity
@Table(name = "signalement",
        indexes = {
                @Index(name = "ix_signalement_cours", columnList = "coursId, statut"),
                @Index(name = "ix_signalement_cible", columnList = "cibleType, cibleId"),
                @Index(name = "ix_signalement_auteur_cible", columnList = "auteurCibleId")})
public class Signalement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private CibleType cibleType;

    @Column(nullable = false)
    private Long cibleId;

    /** Cours du fil concerné : file de modération du formateur. */
    @Column(nullable = false)
    private Long coursId;

    /** Auteur du message signalé (récidive, historique admin). */
    @Column(nullable = false)
    private Long auteurCibleId;

    /** Null quand le signalement vient de l'IA. */
    private Long auteurId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private MotifSignalement motif;

    @Column(length = 500)
    private String commentaire;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private CategorieModeration categorieIa;

    private Double scoreIa;

    @Column(length = 300)
    private String raisonIa;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private StatutSignalement statut = StatutSignalement.OUVERT;

    /** Transmis à l'administration par le formateur. */
    @Column(nullable = false)
    private boolean escalade;

    private Long decideParId;

    @Column(length = 500)
    private String decision;

    private LocalDateTime decideLe;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public CibleType getCibleType() { return cibleType; }
    public void setCibleType(CibleType cibleType) { this.cibleType = cibleType; }

    public Long getCibleId() { return cibleId; }
    public void setCibleId(Long cibleId) { this.cibleId = cibleId; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public Long getAuteurCibleId() { return auteurCibleId; }
    public void setAuteurCibleId(Long auteurCibleId) { this.auteurCibleId = auteurCibleId; }

    public Long getAuteurId() { return auteurId; }
    public void setAuteurId(Long auteurId) { this.auteurId = auteurId; }

    public MotifSignalement getMotif() { return motif; }
    public void setMotif(MotifSignalement motif) { this.motif = motif; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public CategorieModeration getCategorieIa() { return categorieIa; }
    public void setCategorieIa(CategorieModeration categorieIa) { this.categorieIa = categorieIa; }

    public Double getScoreIa() { return scoreIa; }
    public void setScoreIa(Double scoreIa) { this.scoreIa = scoreIa; }

    public String getRaisonIa() { return raisonIa; }
    public void setRaisonIa(String raisonIa) { this.raisonIa = raisonIa; }

    public StatutSignalement getStatut() { return statut; }
    public void setStatut(StatutSignalement statut) { this.statut = statut; }

    public boolean isEscalade() { return escalade; }
    public void setEscalade(boolean escalade) { this.escalade = escalade; }

    public Long getDecideParId() { return decideParId; }
    public void setDecideParId(Long decideParId) { this.decideParId = decideParId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public LocalDateTime getDecideLe() { return decideLe; }
    public void setDecideLe(LocalDateTime decideLe) { this.decideLe = decideLe; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

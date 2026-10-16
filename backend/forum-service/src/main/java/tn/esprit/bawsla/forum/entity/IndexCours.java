package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.StatutIndex;

/**
 * État de l'indexation d'un cours : réindexé seulement si le hash SHA-256 du texte source a changé.
 */
@Entity
@Table(name = "index_cours")
public class IndexCours {

    @Id
    private Long coursId;

    @Column(length = 64)
    private String hashSource;

    @Column(nullable = false)
    private int nbPassages;

    @Column(length = 80)
    private String modeleEmbedding;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private StatutIndex statut;

    /** Texte de démonstration (profil dev) car le texte du cours n'a pas répondu. */
    @Column(nullable = false)
    private boolean sourceDemo;

    private LocalDateTime indexeLe;

    @Column(length = 500)
    private String erreur;

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public String getHashSource() { return hashSource; }
    public void setHashSource(String hashSource) { this.hashSource = hashSource; }

    public int getNbPassages() { return nbPassages; }
    public void setNbPassages(int nbPassages) { this.nbPassages = nbPassages; }

    public String getModeleEmbedding() { return modeleEmbedding; }
    public void setModeleEmbedding(String modeleEmbedding) { this.modeleEmbedding = modeleEmbedding; }

    public StatutIndex getStatut() { return statut; }
    public void setStatut(StatutIndex statut) { this.statut = statut; }

    public boolean isSourceDemo() { return sourceDemo; }
    public void setSourceDemo(boolean sourceDemo) { this.sourceDemo = sourceDemo; }

    public LocalDateTime getIndexeLe() { return indexeLe; }
    public void setIndexeLe(LocalDateTime indexeLe) { this.indexeLe = indexeLe; }

    public String getErreur() { return erreur; }
    public void setErreur(String erreur) { this.erreur = erreur; }
}

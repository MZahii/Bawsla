package tn.esprit.bawsla.cours.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Notion d'une fiche de révision, avec l'extrait du cours qui la justifie. */
@Entity
@Table(name = "notion")
public class Notion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fiche_id", nullable = false)
    private FicheRevision fiche;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String definition;

    @Column(columnDefinition = "TEXT")
    private String extraitSource;

    /** false : l'extrait cité est introuvable dans le texte du cours (notion suspecte). */
    @Column(nullable = false)
    private boolean justifiee = true;

    public Long getId() { return id; }

    public FicheRevision getFiche() { return fiche; }
    public void setFiche(FicheRevision fiche) { this.fiche = fiche; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public String getExtraitSource() { return extraitSource; }
    public void setExtraitSource(String extraitSource) { this.extraitSource = extraitSource; }

    public boolean isJustifiee() { return justifiee; }
    public void setJustifiee(boolean justifiee) { this.justifiee = justifiee; }
}

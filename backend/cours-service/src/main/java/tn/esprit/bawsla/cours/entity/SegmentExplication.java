package tn.esprit.bawsla.cours.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un segment du script d'une explication : qui parle, quoi, et d'où ça vient dans le cours (extrait, page). */
@Entity
@Table(name = "segment_explication")
public class SegmentExplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explication_id", nullable = false)
    private ExplicationAudio explication;

    @Column(nullable = false)
    private int ordre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeSegment type = TypeSegment.EXPLICATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Locuteur locuteur = Locuteur.PROF;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String texte;

    @Column(length = 200)
    private String notion;

    /** Passage du cours recopié mot pour mot, qui justifie le segment (toujours dans la langue du cours). */
    @Column(columnDefinition = "TEXT")
    private String extraitSource;

    /** Page (ou numéro de chapitre) où l'extrait a été retrouvé. */
    private Integer page;

    /** false : extrait manquant ou introuvable dans le cours (segment suspect). */
    @Column(nullable = false)
    private boolean justifiee = true;

    /** true : analogie, partie libre qui ne vient pas du cours. */
    @Column(nullable = false)
    private boolean analogie;

    private Long debutMs;

    private Long finMs;

    public SegmentExplication copierPour(ExplicationAudio autre) {
        SegmentExplication copie = new SegmentExplication();
        copie.explication = autre;
        copie.ordre = ordre;
        copie.type = type;
        copie.locuteur = locuteur;
        copie.texte = texte;
        copie.notion = notion;
        copie.extraitSource = extraitSource;
        copie.page = page;
        copie.justifiee = justifiee;
        copie.analogie = analogie;
        copie.debutMs = debutMs;
        copie.finMs = finMs;
        return copie;
    }

    public Long getId() { return id; }

    public ExplicationAudio getExplication() { return explication; }
    public void setExplication(ExplicationAudio explication) { this.explication = explication; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }

    public TypeSegment getType() { return type; }
    public void setType(TypeSegment type) { this.type = type; }

    public Locuteur getLocuteur() { return locuteur; }
    public void setLocuteur(Locuteur locuteur) { this.locuteur = locuteur; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public String getNotion() { return notion; }
    public void setNotion(String notion) { this.notion = notion; }

    public String getExtraitSource() { return extraitSource; }
    public void setExtraitSource(String extraitSource) { this.extraitSource = extraitSource; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public boolean isJustifiee() { return justifiee; }
    public void setJustifiee(boolean justifiee) { this.justifiee = justifiee; }

    public boolean isAnalogie() { return analogie; }
    public void setAnalogie(boolean analogie) { this.analogie = analogie; }

    public Long getDebutMs() { return debutMs; }
    public void setDebutMs(Long debutMs) { this.debutMs = debutMs; }

    public Long getFinMs() { return finMs; }
    public void setFinMs(Long finMs) { this.finMs = finMs; }
}

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

/** Une phrase du résumé local, avec la phrase du cours qui la justifie et sa page. */
@Entity
@Table(name = "phrase_resume_local")
public class PhraseResumeLocal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private ResumeLocal resume;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    /** Phrase du cours la plus proche (pour un résumé extractif, la phrase elle-même). Réservée au formateur. */
    @Column(columnDefinition = "TEXT")
    private String extrait;

    private Integer page;

    /** false : aucune phrase du cours ne justifie assez cette phrase. Réservé au formateur. */
    @Column(nullable = false)
    private boolean justifiee = true;

    public Long getId() { return id; }
    public ResumeLocal getResume() { return resume; }
    public void setResume(ResumeLocal resume) { this.resume = resume; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }
    public String getExtrait() { return extrait; }
    public void setExtrait(String extrait) { this.extrait = extrait; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public boolean isJustifiee() { return justifiee; }
    public void setJustifiee(boolean justifiee) { this.justifiee = justifiee; }
}

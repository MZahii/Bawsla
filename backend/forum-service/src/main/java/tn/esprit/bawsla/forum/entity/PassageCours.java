package tn.esprit.bawsla.forum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Passage d'un cours (~800 caractères) et son vecteur : base vectorielle des cours pour le RAG.
 * Index FULLTEXT(texte) créé au démarrage.
 */
@Entity
@Table(name = "passage_cours",
        indexes = {
                @Index(name = "ix_passage_cours_cours", columnList = "coursId")})
public class PassageCours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    private Long chapitreId;

    private Integer page;

    @Column(nullable = false)
    private int ordre;

    @Column(length = 200)
    private String titreSection;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    @Convert(converter = VecteurConverter.class)
    @Column(columnDefinition = "BLOB")
    private float[] embedding;

    @Column(length = 80)
    private String modeleEmbedding;

    public Long getId() { return id; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public Long getChapitreId() { return chapitreId; }
    public void setChapitreId(Long chapitreId) { this.chapitreId = chapitreId; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }

    public String getTitreSection() { return titreSection; }
    public void setTitreSection(String titreSection) { this.titreSection = titreSection; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public String getModeleEmbedding() { return modeleEmbedding; }
    public void setModeleEmbedding(String modeleEmbedding) { this.modeleEmbedding = modeleEmbedding; }
}

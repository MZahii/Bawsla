package tn.esprit.bawsla.cours.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Texte d'une page du cours : extrait du PDF (PDFBOX), ou lu par l'IA (VISION) sur une page scannée ou une image déposée par le formateur.
 * Les pages du PDF sont remplacées à chaque nouvel upload ; les pages-images (imageStockage renseigné) sont conservées et renumérotées.
 */
@Entity
@Table(name = "page_document", indexes = @Index(name = "idx_page_cours", columnList = "coursId"))
public class PageDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false)
    private int numero;

    @Column(length = 200000)
    private String texte;

    /** Origine du texte. Null pour les pages créées avant l'ajout de la vision : c'est alors PDFBOX. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SourcePage source = SourcePage.PDFBOX;

    /** Nom UUID de l'image déposée (dossier de stockage « pages »), null pour une page du PDF. */
    @Column(length = 60)
    private String imageStockage;

    /** Moteur qui a lu la page par vision (« gemini », « local · Florence-2 »…). Null : page non lue par vision, ou lue avant l'ajout de ce champ. */
    @Column(length = 60)
    private String moteur;

    /** Le formateur a corrigé le texte à la main. */
    private Boolean modifie;

    protected PageDocument() {
    }

    public PageDocument(Long coursId, int numero, String texte) {
        this.coursId = coursId;
        this.numero = numero;
        this.texte = texte;
    }

    /** Page-image : vide jusqu'à la lecture par vision. */
    public static PageDocument image(Long coursId, int numero, String imageStockage) {
        PageDocument p = new PageDocument(coursId, numero, "");
        p.source = SourcePage.VISION;
        p.imageStockage = imageStockage;
        return p;
    }

    public Long getId() { return id; }
    public Long getCoursId() { return coursId; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }
    public SourcePage getSource() { return source == null ? SourcePage.PDFBOX : source; }
    public void setSource(SourcePage source) { this.source = source; }
    public String getImageStockage() { return imageStockage; }
    public String getMoteur() { return moteur; }
    public void setMoteur(String moteur) { this.moteur = moteur == null || moteur.isBlank() ? null : moteur.strip(); }
    public boolean isModifie() { return Boolean.TRUE.equals(modifie); }
    public void setModifie(boolean modifie) { this.modifie = modifie; }
}

package tn.esprit.bawsla.cours.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Texte extrait d'une page du PDF d'un cours (remplacé à chaque nouvel upload). */
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

    protected PageDocument() {
    }

    public PageDocument(Long coursId, int numero, String texte) {
        this.coursId = coursId;
        this.numero = numero;
        this.texte = texte;
    }

    public Long getId() { return id; }
    public Long getCoursId() { return coursId; }
    public int getNumero() { return numero; }
    public String getTexte() { return texte; }
}

package tn.esprit.bawsla.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Compétence (notion) évaluée par des questions, propre à un cours. Les erreurs des apprenants sont agrégées
 * par compétence pour le contrat notions-ratees.
 */
@Entity
@Table(name = "competence", uniqueConstraints = @UniqueConstraint(columnNames = {"coursId", "libelle"}))
public class Competence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(nullable = false)
    private Long coursId;

    protected Competence() {
    }

    public Competence(String libelle, Long coursId) {
        this.libelle = libelle;
        this.coursId = coursId;
    }

    public Long getId() { return id; }

    public String getLibelle() { return libelle; }

    public Long getCoursId() { return coursId; }
}

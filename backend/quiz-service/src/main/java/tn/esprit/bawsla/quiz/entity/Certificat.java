package tn.esprit.bawsla.quiz.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Certificat délivré quand un apprenant réussit l'examen final d'un cours. Le numéro est public et vérifiable ;
 * le certificat ne contient aucun nom (l'identité reste dans user-service).
 */
@Entity
@Table(name = "certificat", uniqueConstraints = @UniqueConstraint(columnNames = {"etudiantId", "coursId"}))
public class Certificat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @Column(nullable = false)
    private Long etudiantId;

    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false)
    private Long quizId;

    @Column(nullable = false, length = 200)
    private String titreExamen;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateEmission;

    @PrePersist
    void onCreate() {
        dateEmission = LocalDateTime.now();
    }

    protected Certificat() {
    }

    public Certificat(String numero, Long etudiantId, Quiz examen, int score) {
        this.numero = numero;
        this.etudiantId = etudiantId;
        this.coursId = examen.getCoursId();
        this.quizId = examen.getId();
        this.titreExamen = examen.getTitre();
        this.score = score;
    }

    public Long getId() { return id; }

    public String getNumero() { return numero; }

    public Long getEtudiantId() { return etudiantId; }

    public Long getCoursId() { return coursId; }

    public Long getQuizId() { return quizId; }

    public String getTitreExamen() { return titreExamen; }

    public int getScore() { return score; }

    public LocalDateTime getDateEmission() { return dateEmission; }
}

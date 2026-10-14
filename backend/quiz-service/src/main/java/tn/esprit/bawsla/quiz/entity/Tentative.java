package tn.esprit.bawsla.quiz.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Un passage de quiz par un apprenant, corrigé côté serveur. etudiantId vient de user-service (pas de clé étrangère). */
@Entity
@Table(name = "tentative")
public class Tentative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @Column(nullable = false)
    private Long etudiantId;

    @Column(nullable = false)
    private int nbCorrectes;

    @Column(nullable = false)
    private int nbQuestions;

    /** Score en pourcentage, arrondi (0 à 100). */
    @Column(nullable = false)
    private int pourcentage;

    @Column(nullable = false)
    private boolean reussie;

    @OneToMany(mappedBy = "tentative", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reponse> reponses = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateTentative;

    @PrePersist
    void onCreate() {
        dateTentative = LocalDateTime.now();
    }

    protected Tentative() {
    }

    public Tentative(Quiz quiz, Long etudiantId) {
        this.quiz = quiz;
        this.etudiantId = etudiantId;
    }

    public void ajouterReponse(Reponse reponse) {
        reponse.setTentative(this);
        reponses.add(reponse);
    }

    /** Calcule le score à partir des réponses enregistrées. */
    public void corriger(int nbQuestionsDuQuiz) {
        nbQuestions = nbQuestionsDuQuiz;
        nbCorrectes = (int) reponses.stream().filter(Reponse::isCorrecte).count();
        pourcentage = nbQuestions == 0 ? 0 : Math.round(nbCorrectes * 100f / nbQuestions);
        reussie = pourcentage >= quiz.getSeuilReussite();
    }

    public Long getId() { return id; }

    public Quiz getQuiz() { return quiz; }

    public Long getEtudiantId() { return etudiantId; }

    public int getNbCorrectes() { return nbCorrectes; }

    public int getNbQuestions() { return nbQuestions; }

    public int getPourcentage() { return pourcentage; }

    public boolean isReussie() { return reussie; }

    public List<Reponse> getReponses() { return reponses; }

    public LocalDateTime getDateTentative() { return dateTentative; }
}

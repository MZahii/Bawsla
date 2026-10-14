package tn.esprit.bawsla.quiz.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Un quiz de chapitre ou l'examen final d'un cours : des QCM, en brouillon jusqu'à la relecture du formateur.
 * coursId et enseignantId sont des IDENTIFIANTS d'autres services : jamais de clé étrangère.
 */
@Entity
@Table(name = "quiz")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Cours de cours-service, vérifié via Feign à la création. */
    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false)
    private Long enseignantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeQuiz type = TypeQuiz.QUIZ;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutQuiz statut = StatutQuiz.BROUILLON;

    /** Thème donné à l'IA (ou saisi à la main). */
    @Column(length = 200)
    private String theme;

    /** Discipline du cours (Mathématiques, Histoire…), transmise au prompt de génération. */
    @Column(length = 80)
    private String matiere;

    /** FACILE, INTERMEDIAIRE, AVANCE, DIFFICILE ou MASTER. */
    @Column(length = 20)
    private String difficulte;

    /** Pourcentage minimal pour réussir (examen final : délivre le certificat). */
    @Column(nullable = false)
    private int seuilReussite = 70;

    /** Nombre maximal de tentatives par apprenant ; 0 = illimité (quiz de chapitre). */
    @Column(nullable = false)
    private int tentativesMax;

    @Column(nullable = false)
    private int dureeMinutes = 15;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Question> questions = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    private LocalDateTime datePublication;

    @PrePersist
    void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    public void ajouterQuestion(Question question) {
        question.setQuiz(this);
        question.setOrdre(questions.size());
        questions.add(question);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public Long getEnseignantId() { return enseignantId; }
    public void setEnseignantId(Long enseignantId) { this.enseignantId = enseignantId; }

    public TypeQuiz getType() { return type; }
    public void setType(TypeQuiz type) { this.type = type; }

    public StatutQuiz getStatut() { return statut; }
    public void setStatut(StatutQuiz statut) { this.statut = statut; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getMatiere() { return matiere; }
    public void setMatiere(String matiere) { this.matiere = matiere; }

    public String getDifficulte() { return difficulte; }
    public void setDifficulte(String difficulte) { this.difficulte = difficulte; }

    public int getSeuilReussite() { return seuilReussite; }
    public void setSeuilReussite(int seuilReussite) { this.seuilReussite = seuilReussite; }

    public int getTentativesMax() { return tentativesMax; }
    public void setTentativesMax(int tentativesMax) { this.tentativesMax = tentativesMax; }

    public int getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(int dureeMinutes) { this.dureeMinutes = dureeMinutes; }

    public List<Question> getQuestions() { return questions; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public LocalDateTime getDateModification() { return dateModification; }

    public LocalDateTime getDatePublication() { return datePublication; }
    public void setDatePublication(LocalDateTime datePublication) { this.datePublication = datePublication; }
}

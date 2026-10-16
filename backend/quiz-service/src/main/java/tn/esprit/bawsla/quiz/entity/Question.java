package tn.esprit.bawsla.quiz.entity;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Une question QCM d'un quiz, rattachée à une compétence et à un niveau de Bloom. */
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @Column(nullable = false, length = 1000)
    private String enonce;

    /** MEMORISER, COMPRENDRE, APPLIQUER, ANALYSER, EVALUER ou CREER. */
    @Column(length = 20)
    private String niveauBloom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_id")
    private Competence competence;

    @Column(length = 1000)
    private String explication;

    @Column(nullable = false)
    private int ordre;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Option> options = new ArrayList<>();

    public void ajouterOption(Option option) {
        option.setQuestion(this);
        option.setOrdre(options.size());
        options.add(option);
    }

    /** La bonne réponse (exactement une par question, vérifié à l'enregistrement). */
    public Option bonneOption() {
        return options.stream().filter(Option::isEstCorrecte).findFirst().orElseThrow();
    }

    public Long getId() { return id; }

    public Quiz getQuiz() { return quiz; }
    public void setQuiz(Quiz quiz) { this.quiz = quiz; }

    public String getEnonce() { return enonce; }
    public void setEnonce(String enonce) { this.enonce = enonce; }

    public String getNiveauBloom() { return niveauBloom; }
    public void setNiveauBloom(String niveauBloom) { this.niveauBloom = niveauBloom; }

    public Competence getCompetence() { return competence; }
    public void setCompetence(Competence competence) { this.competence = competence; }

    public String getExplication() { return explication; }
    public void setExplication(String explication) { this.explication = explication; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }

    public List<Option> getOptions() { return options; }
}

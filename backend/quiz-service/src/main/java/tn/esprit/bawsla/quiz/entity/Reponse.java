package tn.esprit.bawsla.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** La réponse d'un apprenant à une question, lors d'une tentative. optionChoisie est null si la question est sautée. */
@Entity
@Table(name = "reponse")
public class Reponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tentative_id")
    private Tentative tentative;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private Option optionChoisie;

    @Column(nullable = false)
    private boolean correcte;

    protected Reponse() {
    }

    public Reponse(Question question, Option optionChoisie) {
        this.question = question;
        this.optionChoisie = optionChoisie;
        this.correcte = optionChoisie != null && optionChoisie.isEstCorrecte();
    }

    public Long getId() { return id; }

    public Tentative getTentative() { return tentative; }
    public void setTentative(Tentative tentative) { this.tentative = tentative; }

    public Question getQuestion() { return question; }

    public Option getOptionChoisie() { return optionChoisie; }

    public boolean isCorrecte() { return correcte; }

    /** Diagnostic de base d'une erreur : l'erreur portée par le distracteur choisi. null si la réponse est juste. */
    public String diagnostic() {
        if (correcte) {
            return null;
        }
        if (optionChoisie == null) {
            return "Question laissée sans réponse.";
        }
        return optionChoisie.getErreurAssociee() != null ? optionChoisie.getErreurAssociee()
                : "Réponse « " + optionChoisie.getTexte() + " » incorrecte.";
    }
}

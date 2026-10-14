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

/**
 * Un choix de réponse. Chaque distracteur porte l'erreur de raisonnement qu'il révèle (erreurAssociee) :
 * c'est le point de départ du diagnostic de remédiation. Ces deux champs ne sont jamais envoyés à l'apprenant
 * avant qu'il ait répondu. Table « option_reponse » : OPTION est un mot réservé de MySQL.
 */
@Entity
@Table(name = "option_reponse")
public class Option {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(nullable = false, length = 300)
    private String texte;

    @Column(nullable = false)
    private boolean estCorrecte;

    @Column(length = 300)
    private String erreurAssociee;

    @Column(nullable = false)
    private int ordre;

    protected Option() {
    }

    public Option(String texte, boolean estCorrecte, String erreurAssociee) {
        this.texte = texte;
        this.estCorrecte = estCorrecte;
        this.erreurAssociee = erreurAssociee;
    }

    public Long getId() { return id; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public String getTexte() { return texte; }

    public boolean isEstCorrecte() { return estCorrecte; }

    public String getErreurAssociee() { return erreurAssociee; }

    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
}

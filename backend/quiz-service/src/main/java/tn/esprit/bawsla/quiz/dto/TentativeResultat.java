package tn.esprit.bawsla.quiz.dto;

import java.util.List;

import tn.esprit.bawsla.quiz.entity.Option;
import tn.esprit.bawsla.quiz.entity.Question;
import tn.esprit.bawsla.quiz.entity.Reponse;
import tn.esprit.bawsla.quiz.entity.Tentative;

/**
 * Correction calculée par le serveur. Les bonnes réponses ne sont révélées qu'ici, après la soumission.
 * certificatNumero est renseigné quand cette tentative réussit l'examen final.
 */
public record TentativeResultat(Long tentativeId, Long quizId, int nbCorrectes, int nbQuestions, int pourcentage,
                                boolean reussie, int seuilReussite, String certificatNumero,
                                List<Correction> corrections) {

    public static TentativeResultat from(Tentative t, String certificatNumero) {
        return new TentativeResultat(t.getId(), t.getQuiz().getId(), t.getNbCorrectes(), t.getNbQuestions(),
                t.getPourcentage(), t.isReussie(), t.getQuiz().getSeuilReussite(), certificatNumero,
                t.getReponses().stream().map(Correction::from).toList());
    }

    /** diagnostic : l'erreur de raisonnement associée au distracteur choisi (null si la réponse est juste). */
    public record Correction(Long reponseId, Long questionId, String enonce, String competence, String niveauBloom,
                             Long optionChoisieId, Long optionCorrecteId, boolean correcte, String explication,
                             String diagnostic) {

        static Correction from(Reponse r) {
            Question q = r.getQuestion();
            Option choisie = r.getOptionChoisie();
            return new Correction(r.getId(), q.getId(), q.getEnonce(),
                    q.getCompetence() == null ? null : q.getCompetence().getLibelle(), q.getNiveauBloom(),
                    choisie == null ? null : choisie.getId(), q.bonneOption().getId(), r.isCorrecte(),
                    q.getExplication(), r.diagnostic());
        }
    }
}

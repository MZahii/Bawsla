package tn.esprit.bawsla.quiz.dto;

import java.util.List;

import tn.esprit.bawsla.quiz.entity.Option;
import tn.esprit.bawsla.quiz.entity.Question;
import tn.esprit.bawsla.quiz.entity.Quiz;

/** Vue formateur (éditeur) : questions avec bonnes réponses et erreurs associées. Réservée au propriétaire ou à l'admin. */
public record QuizDetailResponse(QuizResponse quiz, List<QuestionDetail> questions) {

    public static QuizDetailResponse from(Quiz q) {
        return new QuizDetailResponse(QuizResponse.from(q), q.getQuestions().stream().map(QuestionDetail::from).toList());
    }

    public record QuestionDetail(Long id, String enonce, String niveauBloom, String competence, String explication,
                                 List<OptionDetail> choix) {

        static QuestionDetail from(Question q) {
            return new QuestionDetail(q.getId(), q.getEnonce(), q.getNiveauBloom(),
                    q.getCompetence() == null ? null : q.getCompetence().getLibelle(), q.getExplication(),
                    q.getOptions().stream().map(OptionDetail::from).toList());
        }
    }

    public record OptionDetail(Long id, String texte, boolean estCorrecte, String erreurAssociee) {

        static OptionDetail from(Option o) {
            return new OptionDetail(o.getId(), o.getTexte(), o.isEstCorrecte(), o.getErreurAssociee());
        }
    }
}

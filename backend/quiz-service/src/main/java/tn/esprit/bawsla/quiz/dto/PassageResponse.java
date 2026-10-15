package tn.esprit.bawsla.quiz.dto;

import java.util.List;

import tn.esprit.bawsla.quiz.entity.Option;
import tn.esprit.bawsla.quiz.entity.Question;
import tn.esprit.bawsla.quiz.entity.Quiz;

/**
 * Vue apprenant d'un quiz à passer : ni bonne réponse, ni erreur associée, ni explication (elles arrivent avec la
 * correction, après la soumission). tentativesRestantes est null quand les tentatives sont illimitées.
 */
public record PassageResponse(QuizResponse quiz, List<QuestionPassage> questions, long tentativesFaites,
                              Integer tentativesRestantes) {

    public static PassageResponse from(Quiz q, long tentativesFaites) {
        Integer restantes = q.getTentativesMax() == 0 ? null : (int) Math.max(0, q.getTentativesMax() - tentativesFaites);
        return new PassageResponse(QuizResponse.from(q), q.getQuestions().stream().map(QuestionPassage::from).toList(),
                tentativesFaites, restantes);
    }

    public record QuestionPassage(Long id, String enonce, String niveauBloom, String competence,
                                  List<OptionPassage> choix) {

        static QuestionPassage from(Question q) {
            return new QuestionPassage(q.getId(), q.getEnonce(), q.getNiveauBloom(),
                    q.getCompetence() == null ? null : q.getCompetence().getLibelle(),
                    q.getOptions().stream().map(OptionPassage::from).toList());
        }
    }

    public record OptionPassage(Long id, String texte) {

        static OptionPassage from(Option o) {
            return new OptionPassage(o.getId(), o.getTexte());
        }
    }
}

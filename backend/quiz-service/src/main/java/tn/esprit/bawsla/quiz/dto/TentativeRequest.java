package tn.esprit.bawsla.quiz.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Réponses d'un apprenant ; optionId null = question sautée (comptée fausse). */
public record TentativeRequest(@NotNull(message = "Les réponses sont obligatoires") @Valid List<ReponseRequest> reponses) {

    public record ReponseRequest(@NotNull Long questionId, Long optionId) {
    }
}

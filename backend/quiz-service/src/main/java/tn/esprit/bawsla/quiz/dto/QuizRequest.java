package tn.esprit.bawsla.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuizRequest(
        @NotBlank(message = "Le titre est obligatoire") @Size(max = 200) String titre,
        String description,
        @NotNull(message = "Le cours est obligatoire") Long coursId) {
}

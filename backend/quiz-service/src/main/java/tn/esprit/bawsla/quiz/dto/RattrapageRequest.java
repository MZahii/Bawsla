package tn.esprit.bawsla.quiz.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RattrapageRequest(@NotNull(message = "Le choix est obligatoire") @Min(0) Integer indexChoix) {
}

package tn.esprit.bawsla.quiz.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import tn.esprit.bawsla.quiz.entity.TypeQuiz;

/**
 * Création ou remplacement complet d'un quiz (questions comprises). Un quiz généré par l'IA arrive ici après
 * relecture du formateur, avec ses seules questions validées. Champs optionnels : valeurs par défaut du type.
 */
public record QuizRequest(
        @NotBlank(message = "Le titre est obligatoire") @Size(max = 200) String titre,
        String description,
        @NotNull(message = "Le cours est obligatoire") Long coursId,
        TypeQuiz type,
        @Size(max = 200) String theme,
        @Size(max = 80) String matiere,
        @Pattern(regexp = "FACILE|INTERMEDIAIRE|AVANCE|DIFFICILE|MASTER", message = "Difficulté inconnue") String difficulte,
        @Min(0) @Max(100) Integer seuilReussite,
        @Min(0) @Max(20) Integer tentativesMax,
        @Min(1) @Max(240) Integer dureeMinutes,
        @Valid @Size(max = 50, message = "50 questions au maximum") List<QuestionRequest> questions) {

    public record QuestionRequest(
            @NotBlank(message = "L'énoncé est obligatoire") @Size(max = 1000) String enonce,
            @Pattern(regexp = "MEMORISER|COMPRENDRE|APPLIQUER|ANALYSER|EVALUER|CREER", message = "Niveau de Bloom inconnu")
            String niveauBloom,
            @Size(max = 150) String competence,
            @Size(max = 1000) String explication,
            @NotNull @Valid @Size(min = 2, max = 6, message = "Entre 2 et 6 choix par question") List<OptionRequest> choix) {
    }

    public record OptionRequest(
            @NotBlank(message = "Le texte du choix est obligatoire") @Size(max = 300) String texte,
            boolean estCorrecte,
            @Size(max = 300) String erreurAssociee) {
    }
}

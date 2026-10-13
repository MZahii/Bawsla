package tn.esprit.bawsla.cours.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.cours.entity.Niveau;

/**
 * Corps de POST et PUT /api/cours. Le PDF et l'image passent par leurs routes d'upload, le statut par PATCH /statut.
 * {@code prix} absent : inchangé en modification, 0 (gratuit) à la création.
 *
 * <p>{@code categorie} est la matière du cours, obligatoire et choisie dans la liste commune avec le module Quiz (voir
 * {@link MatiereValide}). {@code categoriePrecision} (2 à 80 caractères) précise la matière « Autre » et n'est acceptée que pour elle.</p>
 */
@MatiereValide
public record CoursRequest(
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "200 caractères maximum")
        String titre,
        String description,
        String categorie,
        String categoriePrecision,
        Niveau niveau,
        @DecimalMin(value = "0", message = "Le prix ne peut pas être négatif")
        @Digits(integer = 7, fraction = 2, message = "Prix invalide (2 décimales maximum)")
        BigDecimal prix) {
}

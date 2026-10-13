package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Suppression (désactivation) de son propre compte : le mot de passe confirme l'intention. */
public record DesactivationRequest(@NotBlank(message = "Le mot de passe est obligatoire") String motDePasse) {
}

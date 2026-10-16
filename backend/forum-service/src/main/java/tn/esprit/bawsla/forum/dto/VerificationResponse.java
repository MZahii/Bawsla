package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** donneesPersonnelles : le message contient un email ou un téléphone ; alertes : phrases à montrer à l'auteur. */
public record VerificationResponse(boolean donneesPersonnelles, List<String> alertes) {
}

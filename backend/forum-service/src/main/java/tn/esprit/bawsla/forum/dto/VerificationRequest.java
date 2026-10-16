package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.Size;

/** Vérification d'un message avant publication (données personnelles), sans rien enregistrer. */
public record VerificationRequest(@Size(max = 300, message = "Le titre fait au plus 300 caractères") String titre,
                                  @Size(max = 20000, message = "Le contenu fait au plus 20000 caractères")
                                  String contenu) {
}

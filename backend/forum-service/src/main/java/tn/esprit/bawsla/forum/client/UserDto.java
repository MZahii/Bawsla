package tn.esprit.bawsla.forum.client;

/** Informations PUBLIQUES d'un membre (contrat 4). Jamais transmises au LLM. */
public record UserDto(Long id, String nom, String prenom, String role) {
}

package tn.esprit.bawsla.quiz.client;

/**
 * Vue LOCALE (côté quiz) d'un utilisateur renvoyé par user-service (contrat 4).
 * Seuls les champs utiles à la publication ; Jackson ignore les autres (nom, prénom ne sont pas lus).
 */
public record UserDto(Long id, String role, boolean formateurValide) {
}

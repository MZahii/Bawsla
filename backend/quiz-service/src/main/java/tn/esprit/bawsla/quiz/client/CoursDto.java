package tn.esprit.bawsla.quiz.client;

/**
 * Vue LOCALE (côté quiz) d'un cours renvoyé par cours-service.
 * Ne contient que les champs utiles au module Quiz ; Jackson ignore les autres.
 */
public record CoursDto(Long id, String titre, String categorie, String niveau, Long enseignantId) {
}

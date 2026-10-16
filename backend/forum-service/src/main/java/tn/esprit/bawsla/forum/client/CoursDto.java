package tn.esprit.bawsla.forum.client;

/** Vue LOCALE d'un cours (contrat 1) : seuls les champs garantis stables ; Jackson ignore les autres. */
public record CoursDto(Long id, String titre, String categorie, String niveau, Long enseignantId) {
}

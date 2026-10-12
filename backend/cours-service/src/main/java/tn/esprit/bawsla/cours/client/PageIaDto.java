package tn.esprit.bawsla.cours.client;

/** Une page du cours (ou un chapitre, pour un cours sans PDF) envoyée à l'IA. */
public record PageIaDto(int numero, String texte) {
}

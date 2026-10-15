package tn.esprit.bawsla.forum.dto;

/** Ligne du classement hebdomadaire des contributeurs d'un cours. */
public record ClassementEntree(int rang, AuteurResponse auteur, int points) {
}

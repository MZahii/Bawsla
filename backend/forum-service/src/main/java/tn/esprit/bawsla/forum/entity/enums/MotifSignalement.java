package tn.esprit.bawsla.forum.entity.enums;

/** Motif choisi par l'utilisateur qui signale (l'IA utilise la catégorie la plus proche). */
public enum MotifSignalement {
    HORS_SUJET,
    IRRESPECTUEUX,
    SPAM,
    /** Partage de réponses d'examen. */
    FUITE_EXAMEN,
    AUTRE
}

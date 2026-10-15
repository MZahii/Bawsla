package tn.esprit.bawsla.forum.entity.enums;

/** Type de message visé par un vote, un signalement ou un mouvement de points. */
public enum CibleType {
    DISCUSSION,
    REPONSE,
    /** Signalement uniquement (pas de vote). */
    COMMENTAIRE
}

package tn.esprit.bawsla.forum.entity.enums;

/** Événements qui produisent une notification. */
public enum TypeNotification {
    /** Réponse dans un fil suivi. */
    REPONSE,
    COMMENTAIRE,
    /** @Prénom Nom dans un message. */
    MENTION,
    REPONSE_ACCEPTEE,
    /** Réponse approuvée par le formateur. */
    APPROBATION,
    /** La réponse de La Vigie est disponible. */
    VIGIE_PRETE,
    /** Un de mes messages a été masqué par la modération. */
    MESSAGE_MASQUE,
    /** Formateur : La Vigie s'est abstenue, le cours ne traite pas ce point. */
    HORS_COURS,
    /** Formateur : un apprenant a demandé une réponse générale (contenu manquant). */
    DEMANDE_GENERALE,
    /** Formateur : nouveau signalement dans un de ses cours. */
    SIGNALEMENT,
    /** Sanction reçue ou levée. */
    SANCTION,
    /** Badge obtenu. */
    BADGE
}

package tn.esprit.bawsla.forum.entity.enums;

/** Actions tracées dans le journal de modération. */
public enum ActionModeration {
    MASQUER,
    /** Masquage automatique par l'IA (score ≥ seuil). */
    MASQUAGE_AUTO,
    RETABLIR,
    VERROUILLER,
    DEVERROUILLER,
    EPINGLER,
    DESEPINGLER,
    DEPLACER,
    FUSIONNER,
    MODIFIER_ETIQUETTES,
    SUPPRIMER,
    DECIDER_SIGNALEMENT,
    ESCALADER,
    SANCTIONNER,
    LEVER_SANCTION,
    VALIDER_VIGIE,
    CORRIGER_VIGIE,
    REJETER_VIGIE,
    MODIFIER_PARAMETRES,
    FUSIONNER_ETIQUETTES,
    RENOMMER_ETIQUETTE
}

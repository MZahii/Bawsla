package tn.esprit.bawsla.forum.entity.enums;

/** Cycle de vie d'une réponse de La Vigie. */
public enum StatutReponseIa {
    /** Génération asynchrone en cours (« La Vigie cherche dans le cours… »). */
    EN_ATTENTE,
    GENEREE,
    /** Vérifiée par le formateur. */
    VALIDEE,
    CORRIGEE,
    REJETEE,
    /** Abstention : rien de pertinent dans les sources. */
    HORS_COURS,
    ERREUR
}

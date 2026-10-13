package tn.esprit.bawsla.cours.entity;

/**
 * Étapes d'un traitement IA, dans l'ordre. Fiche : TEXTE, FICHE, AUDIO. Explication : TEXTE, puis SCRIPT (français)
 * ou TRADUCTION (anglais), puis AUDIO (l'aperçu se demande à part).
 */
public enum EtapeIA {
    TEXTE,
    FICHE,
    SCRIPT,
    TRADUCTION,
    /** Résumé automatique local (modèle Hugging Face ou phrases clés), après TEXTE puis avant AUDIO. */
    RESUME,
    AUDIO
}

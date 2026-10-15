package tn.esprit.bawsla.forum.entity.enums;

/** Catégorie renvoyée par la modération (règles sans LLM puis /api/ai/forum/moderer). */
public enum CategorieModeration {
    OK,
    HORS_SUJET,
    IRRESPECTUEUX,
    SPAM,
    FUITE_EXAMEN,
    /** Tentative d'injection de prompt. */
    INJECTION,
    DONNEES_PERSONNELLES
}

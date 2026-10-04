package tn.esprit.bawsla.quiz.dto;

/**
 * Contrat GET /api/quiz/notions-ratees?etudiantId= (consommé par le module User pour le ML).
 * notion : libellé de la notion/compétence ; nbErreurs : nombre de réponses fausses ;
 * tauxReussite : entre 0.0 et 1.0.
 */
public record NotionRateeDto(String notion, Long coursId, int nbErreurs, double tauxReussite) {
}

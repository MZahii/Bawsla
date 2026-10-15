package tn.esprit.bawsla.quiz.dto;

/** Agrégat interne (projection JPQL) : réponses d'un apprenant pour une compétence d'un cours. */
public record NotionStat(String notion, Long coursId, Long nbErreurs, Long nbReponses) {
}

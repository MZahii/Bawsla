package tn.esprit.bawsla.forum.client;

/** Notion ratée d'un apprenant (contrat 3), pour la section « Pour toi ». */
public record NotionRateeDto(String notion, Long coursId, int nbErreurs, double tauxReussite) {
}

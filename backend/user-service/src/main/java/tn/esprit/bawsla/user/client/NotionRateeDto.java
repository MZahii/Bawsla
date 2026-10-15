package tn.esprit.bawsla.user.client;

/** Contrat 3 : une notion ratée par l'apprenant (tauxReussite de 0 à 1). */
public record NotionRateeDto(String notion, Long coursId, int nbErreurs, double tauxReussite) {
}

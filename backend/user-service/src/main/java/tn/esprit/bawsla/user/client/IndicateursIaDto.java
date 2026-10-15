package tn.esprit.bawsla.user.client;

/** Les 11 indicateurs chiffrés envoyés à l'IA (contrat 5) : aucun identifiant. */
public record IndicateursIaDto(double noteMoyenne, double tauxReussite, int nbQuiz, int nbNotionsRatees, int nbErreurs,
                               double progressionMoyenne, int tempsPasseMinutes, int nbCoursSuivis,
                               int nbCoursTermines, int joursActifs30j, int joursDepuisDerniereConnexion) {
}

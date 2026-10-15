package tn.esprit.bawsla.quiz.dto;

/** Correction de la question de rattrapage : la bonne réponse n'est révélée qu'après la réponse de l'apprenant. */
public record RattrapageResultat(boolean correcte, int indexBonne, String explication) {
}

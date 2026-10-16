package tn.esprit.bawsla.quiz.client;

/**
 * Corps de POST /api/ai/quiz/remedier : la question, la réponse donnée et la bonne réponse. Jamais d'identifiant,
 * de nom ni d'email d'apprenant (ai-service refuse tout champ inattendu).
 */
public record RemedierIaRequestDto(String notion, String niveauBloom, String enonce, String choixDonne,
                                   String bonneReponse, String erreurAssociee, String explication) {
}

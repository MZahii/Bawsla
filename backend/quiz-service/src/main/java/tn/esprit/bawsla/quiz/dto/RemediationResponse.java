package tn.esprit.bawsla.quiz.dto;

import java.util.List;

/**
 * Remédiation d'une mauvaise réponse pour l'apprenant. La question de rattrapage est envoyée SANS les bonnes
 * réponses : la vérification se fait côté serveur (POST /remediations/{id}/rattrapage).
 */
public record RemediationResponse(Long notionRateeId, String notion, String diagnostic, String explication,
                                  String conseil, QuestionRattrapage questionRattrapage, boolean resolue,
                                  String fournisseur, List<String> alertes) {

    public record QuestionRattrapage(String enonce, List<String> choix) {
    }
}

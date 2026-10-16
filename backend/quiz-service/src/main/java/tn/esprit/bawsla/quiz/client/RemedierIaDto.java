package tn.esprit.bawsla.quiz.client;

import java.util.List;

/** Réponse de la remédiation IA (raisonnement absent si l'appelant est un apprenant). */
public record RemedierIaDto(String notion, String raisonnement, String explication, String conseil,
                            QuestionIaDto questionRattrapage, String versionPrompt, String fournisseur,
                            List<String> alertes) {

    public record QuestionIaDto(String enonce, String niveauBloom, String competence, List<ChoixIaDto> choix,
                                String explication, String statut, String raisonRejet) {
    }

    public record ChoixIaDto(String texte, boolean estCorrecte, String erreurAssociee) {
    }
}

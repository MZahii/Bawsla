package tn.esprit.bawsla.forum.client;

/** POST /api/ai/forum/moderer : catégorie, score (0 à 1) et raison ; regle = règle sans LLM qui a tranché. */
public final class ModererIaDto {

    private ModererIaDto() {
    }

    public record Requete(String titre, String contenu) {
    }

    public record Reponse(String categorie, double score, String raison, String regle, String versionPrompt,
                          String fournisseur) {
    }
}

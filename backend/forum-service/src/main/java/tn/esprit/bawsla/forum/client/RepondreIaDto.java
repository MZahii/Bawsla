package tn.esprit.bawsla.forum.client;

import java.util.List;

/** POST /api/ai/forum/repondre et /repondre-general (format du cahier des charges, en camelCase). */
public final class RepondreIaDto {

    private RepondreIaDto() {
    }

    /** Passage fourni à La Vigie ; score = similarité avec la question (0 à 1). Aucun identifiant d'utilisateur. */
    public record Passage(Long id, String origine, String cours, String chapitre, Integer page, String texte,
                          double score) {
    }

    public record Requete(String question, String mode, List<Passage> passages) {
    }

    public record Source(int numero, Long id, String origine, String cours, String passage, String chapitre,
                         Integer page, String citation) {
    }

    public record Reponse(String reponse, List<Source> sources, int confiance, boolean horsCours,
                          List<String> indices, String versionPrompt, String fournisseur, List<String> alertes) {
    }

    public record RequeteGenerale(String question) {
    }

    public record ReponseGenerale(String reponse, List<String> incertitudes, List<String> aVerifier,
                                  String versionPrompt, String fournisseur) {
    }
}

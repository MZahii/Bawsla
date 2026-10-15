package tn.esprit.bawsla.forum.client;

import java.util.List;

/** POST /api/ai/forum/resumer, /ameliorer et /nommer-groupes. Aucun nom ni identifiant d'utilisateur. */
public final class AidesIaDto {

    private AidesIaDto() {
    }

    /** role : QUESTION, REPONSE, VIGIE ou COMMENTAIRE. */
    public record MessageFil(String role, String texte) {
    }

    public record ResumerRequete(String titre, List<MessageFil> messages) {
    }

    public record ResumerReponse(List<String> puces, String versionPrompt, String fournisseur) {
    }

    public record AmeliorerRequete(String titre, String contenu, List<String> etiquettesExistantes) {
    }

    public record AmeliorerReponse(String titre, List<String> manques, List<String> etiquettes, boolean repli,
                                   String versionPrompt, String fournisseur) {
    }

    public record GroupeTitres(List<String> titres) {
    }

    public record NommerRequete(List<GroupeTitres> groupes) {
    }

    public record NommerReponse(List<String> noms, boolean repli, String versionPrompt, String fournisseur) {
    }
}

package tn.esprit.bawsla.forum.client;

import java.util.List;

/** POST /api/ai/forum/reformuler : question (et historique de l'assistant privé) vers requête technique. */
public final class ReformulerIaDto {

    private ReformulerIaDto() {
    }

    public record Message(String role, String texte) {
    }

    public record Requete(String question, List<Message> historique) {
    }

    /** repli = true : le LLM n'a pas répondu, requete est la question nettoyée telle quelle. */
    public record Reponse(String requete, String langueDetectee, String versionPrompt, String fournisseur,
                          boolean repli) {
    }
}

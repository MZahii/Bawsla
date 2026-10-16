package tn.esprit.bawsla.forum.client;

import java.util.List;

/** POST /api/ai/forum/vectoriser : requête (textes, tache DOCUMENT ou REQUETE) et réponse (vecteurs normalisés). */
public final class VectoriserIaDto {

    private VectoriserIaDto() {
    }

    public record Requete(List<String> textes, String tache) {
    }

    public record Reponse(List<float[]> vecteurs, String modele, int dimension, String fournisseur) {
    }
}

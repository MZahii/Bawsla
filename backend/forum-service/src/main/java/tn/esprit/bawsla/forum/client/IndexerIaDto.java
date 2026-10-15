package tn.esprit.bawsla.forum.client;

import java.util.List;

/** POST /api/ai/forum/indexer : texte du cours (libre, pages ou chapitres) puis passages vectorisés. */
public final class IndexerIaDto {

    private IndexerIaDto() {
    }

    public record Page(int numero, String texte) {
    }

    public record Chapitre(Long id, String titre, String contenu) {
    }

    public record Requete(Long coursId, String texte, List<Page> pages, List<Chapitre> chapitres) {
    }

    public record Passage(Long chapitreId, Integer page, int ordre, String titreSection, String texte,
                          float[] vecteur) {
    }

    public record Reponse(List<Passage> passages, String modele, int dimension, String fournisseur,
                          int donneesMasquees) {
    }
}

package tn.esprit.bawsla.user.client;

import java.util.List;

public record RecommandationsIaDto(List<CoursRecommande> recommandations, boolean donneesSimulees) {

    public record CoursRecommande(Long coursId, String titre, double score, String raison) {
    }
}

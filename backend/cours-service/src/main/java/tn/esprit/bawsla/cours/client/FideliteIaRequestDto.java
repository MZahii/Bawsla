package tn.esprit.bawsla.cours.client;

import java.util.List;

/** Corps de POST /api/ai/cours/fidelite : la fiche (corrigée) et le texte du cours à comparer. */
public record FideliteIaRequestDto(Fiche fiche, String texte) {

    public record Fiche(String resume, List<String> motsCles, List<NotionIaDto> notions) {
    }
}

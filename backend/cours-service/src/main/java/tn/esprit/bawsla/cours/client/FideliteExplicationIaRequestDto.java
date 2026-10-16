package tn.esprit.bawsla.cours.client;

import java.util.List;

/** Corps de POST /api/ai/cours/explication/fidelite : le script (corrigé) et le texte du cours à comparer. */
public record FideliteExplicationIaRequestDto(List<SegmentIaDto> segments, List<PageIaDto> pages, List<String> notions) {
}

package tn.esprit.bawsla.cours.client;

import java.util.List;

/** Corps de POST /api/ai/cours/explication/traduire : le script français VALIDÉ, à traduire segment par segment. */
public record TraductionIaRequestDto(List<SegmentIaDto> segments, List<PageIaDto> pages, List<String> notions, String langueCible,
                                     String langueSource, String mode) {
}

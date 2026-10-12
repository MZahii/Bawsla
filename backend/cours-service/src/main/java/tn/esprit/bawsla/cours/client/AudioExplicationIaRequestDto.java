package tn.esprit.bawsla.cours.client;

import java.util.List;

/** Corps de POST /api/ai/cours/explication/audio. */
public record AudioExplicationIaRequestDto(List<SegmentIaDto> segments, String langue, String mode) {
}

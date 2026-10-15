package tn.esprit.bawsla.cours.client;

import java.util.List;

/** Corps de POST /api/ai/cours/resume-local. {@code longueur} : courte, moyenne ou longue ; {@code mode} : auto, abstractif ou extractif. */
public record ResumeLocalIaRequestDto(Long coursId, List<PageIaDto> pages, String longueur, String mode) {
}

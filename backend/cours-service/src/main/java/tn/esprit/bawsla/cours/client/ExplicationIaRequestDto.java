package tn.esprit.bawsla.cours.client;

import java.util.List;

import tn.esprit.bawsla.cours.entity.Niveau;

/** Corps de POST /api/ai/cours/explication. {@code notions} : titres de la fiche validée (le plan), vide si elle n'existe pas. */
public record ExplicationIaRequestDto(Long coursId, List<PageIaDto> pages, List<String> notions, Niveau niveau, String mode,
                                      String langue, int dureeMinutes) {
}

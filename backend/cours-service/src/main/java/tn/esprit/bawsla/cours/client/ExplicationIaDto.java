package tn.esprit.bawsla.cours.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Réponse de POST /api/ai/cours/explication et /explication/traduire. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExplicationIaDto(String langue, String mode, List<SegmentIaDto> segments, Integer scoreFidelite, List<String> alertes,
                               String versionPrompt, String modele, Integer nbMots, Integer dureeEstimeeSecondes,
                               Boolean notionsChoisiesParIa) {
}

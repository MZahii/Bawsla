package tn.esprit.bawsla.cours.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Réponse de POST /api/ai/cours/explication/fidelite. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FideliteExplicationIaDto(Integer scoreFidelite, List<String> alertes, List<SegmentIaDto> segments, Integer nbMots,
                                       Integer dureeEstimeeSecondes) {
}

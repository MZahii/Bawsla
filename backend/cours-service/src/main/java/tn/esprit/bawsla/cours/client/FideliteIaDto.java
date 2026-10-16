package tn.esprit.bawsla.cours.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Réponse de POST /api/ai/cours/fidelite : une entrée par notion envoyée, dans le même ordre. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FideliteIaDto(Integer scoreFidelite, List<String> alertes, List<NotionJustifieeDto> notions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotionJustifieeDto(String titre, boolean justifiee) {
    }
}

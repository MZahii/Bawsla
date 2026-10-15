package tn.esprit.bawsla.cours.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tn.esprit.bawsla.cours.entity.Niveau;

/** Réponse de POST /api/ai/cours/fiche. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FicheIaDto(String resume, List<String> motsCles, List<NotionIaDto> notions, Niveau niveau,
                         Integer scoreFidelite, List<String> alertes, String versionPrompt, String modele) {
}

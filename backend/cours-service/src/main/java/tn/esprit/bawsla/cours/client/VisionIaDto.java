package tn.esprit.bawsla.cours.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Réponse de POST /api/ai/cours/vision. {@code vide} : rien de lisible sur la page ({@code texte} est alors vide). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VisionIaDto(String texte, boolean vide, String modele, String fournisseur, String versionPrompt) {
}

package tn.esprit.bawsla.cours.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Réponse de POST /api/ai/cours/explication/audio. L'audio est en base64 dans le JSON pour que le minutage voyage avec
 * lui : un MP3 de 5 minutes pèse environ 6 Mo une fois encodé (voir JacksonConfig pour la limite de lecture).
 * {@code minutageEstime} : minutage réparti au prorata des caractères (Gemini fait un seul appel pour tout le script).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AudioExplicationIaDto(String fournisseur, String mime, String audioBase64, Long dureeMs, Boolean minutageEstime,
                                    List<MinutageIaDto> segments) {
}

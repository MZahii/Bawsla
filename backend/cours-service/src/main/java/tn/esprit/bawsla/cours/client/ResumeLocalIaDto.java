package tn.esprit.bawsla.cours.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Réponse de POST /api/ai/cours/resume-local. {@code modeUtilise} : abstractif (reformulé) ou extractif (phrases clés du cours).
 * {@code scoreReformule} : score du reformulé même s'il a été remplacé par l'extractif (null s'il n'a pas été tenté).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeLocalIaDto(String langue, String modele, String modeUtilise, String resume, List<PhraseIaDto> phrases,
                               Integer scoreFidelite, Integer scoreReformule, List<String> alertes, Long dureeMs) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhraseIaDto(String texte, String extraitSource, Integer page, Boolean justifiee) {
    }
}

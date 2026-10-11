package tn.esprit.bawsla.cours.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Notion échangée avec ai-service. {@code justifiee} n'est renseignée que dans les réponses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotionIaDto(String titre, String definition, String extraitSource, Boolean justifiee) {

    public NotionIaDto(String titre, String definition, String extraitSource) {
        this(titre, definition, extraitSource, null);
    }
}

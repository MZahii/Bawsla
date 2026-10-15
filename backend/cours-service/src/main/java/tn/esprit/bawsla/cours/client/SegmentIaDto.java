package tn.esprit.bawsla.cours.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Segment d'une explication, dans les deux sens. {@code type} : intro, explication, analogie, exemple, demonstration,
 * question ou recap ; {@code locuteur} : prof ou etudiant. {@code justifiee} et {@code analogie} ne figurent que dans
 * les réponses de l'IA.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SegmentIaDto(Integer ordre, String type, String locuteur, String texte, String notion, String extraitSource,
                           Integer page, Boolean justifiee, Boolean analogie) {
}

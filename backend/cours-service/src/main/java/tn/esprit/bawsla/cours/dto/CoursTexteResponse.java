package tn.esprit.bawsla.cours.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contrat GET /api/cours/{id}/texte : texte extrait du PDF (emails et téléphones masqués), consommé par l'IA
 * et le RAG du Forum. {@code pages} et {@code estScanne} sont facultatifs (absents si le cours n'a pas de PDF).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoursTexteResponse(Long coursId, String texte, boolean stub, List<PageTexte> pages, Boolean estScanne) {

    public static CoursTexteResponse sansPdf(Long coursId) {
        return new CoursTexteResponse(coursId, "", false, null, null);
    }
}

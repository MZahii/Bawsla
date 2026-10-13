package tn.esprit.bawsla.cours.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.cours.entity.Notion;

/** {@code justifiee} n'est renvoyée qu'au formateur : l'apprenant ne voit pas le contrôle de fidélité. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotionResponse(Long id, int ordre, String titre, String definition, String extraitSource, Boolean justifiee) {

    public static NotionResponse from(Notion n, boolean gestionnaire) {
        return new NotionResponse(n.getId(), n.getOrdre(), n.getTitre(), n.getDefinition(), n.getExtraitSource(),
                gestionnaire ? n.isJustifiee() : null);
    }
}

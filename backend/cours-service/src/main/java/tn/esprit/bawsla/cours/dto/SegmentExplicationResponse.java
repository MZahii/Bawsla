package tn.esprit.bawsla.cours.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.cours.entity.SegmentExplication;

/**
 * Un segment du script. L'extrait source et le verdict de fidélité ({@code justifiee}) sont réservés au formateur et à
 * l'ADMIN ; l'apprenant voit le texte, le locuteur, la notion, la page source et le minutage.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SegmentExplicationResponse(Long id, int ordre, String type, String locuteur, String texte, String notion, Integer page,
                                         boolean analogie, Long debutMs, Long finMs, String extraitSource, Boolean justifiee) {

    public static SegmentExplicationResponse from(SegmentExplication s, boolean gestionnaire) {
        return new SegmentExplicationResponse(s.getId(), s.getOrdre(), s.getType().code(), s.getLocuteur().code(), s.getTexte(),
                s.getNotion(), s.getPage(), s.isAnalogie(), s.getDebutMs(), s.getFinMs(),
                gestionnaire ? s.getExtraitSource() : null, gestionnaire ? s.isJustifiee() : null);
    }
}

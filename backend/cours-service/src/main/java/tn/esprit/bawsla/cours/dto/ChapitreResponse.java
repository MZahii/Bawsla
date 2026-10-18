package tn.esprit.bawsla.cours.dto;

import tn.esprit.bawsla.cours.entity.Chapitre;

public record ChapitreResponse(Long id, int ordre, String titre, String contenu, int dureeMinutes, String videoUrl) {

    public static ChapitreResponse from(Chapitre c) {
        return new ChapitreResponse(c.getId(), c.getOrdre(), c.getTitre(), c.getContenu(), c.getDureeMinutes(),
                c.getVideoUrl());
    }

    /** Programme seul (visiteur du catalogue) : ni contenu ni vidéo, réservés aux inscrits. */
    public static ChapitreResponse programme(Chapitre c) {
        return new ChapitreResponse(c.getId(), c.getOrdre(), c.getTitre(), null, c.getDureeMinutes(), null);
    }
}

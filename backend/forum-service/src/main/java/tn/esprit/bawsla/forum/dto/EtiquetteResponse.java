package tn.esprit.bawsla.forum.dto;

import tn.esprit.bawsla.forum.entity.Etiquette;

public record EtiquetteResponse(Long id, String libelle, int nbUtilisations) {

    public static EtiquetteResponse from(Etiquette e) {
        return new EtiquetteResponse(e.getId(), e.getLibelle(), e.getNbUtilisations());
    }
}

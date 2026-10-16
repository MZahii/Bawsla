package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.VersionDiscussion;

/** Version précédente d'une discussion (historique des éditions). */
public record VersionResponse(String titre, String contenu, LocalDateTime modifieLe, Long modifieParId) {

    public static VersionResponse from(VersionDiscussion v) {
        return new VersionResponse(v.getTitre(), v.getContenu(), v.getModifieLe(), v.getModifieParId());
    }
}

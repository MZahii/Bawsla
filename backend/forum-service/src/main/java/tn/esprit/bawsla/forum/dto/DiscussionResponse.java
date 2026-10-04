package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.Discussion;

public record DiscussionResponse(Long id, String titre, String contenu, Long coursId, Long auteurId,
                                 LocalDateTime dateCreation) {

    public static DiscussionResponse from(Discussion d) {
        return new DiscussionResponse(d.getId(), d.getTitre(), d.getContenu(), d.getCoursId(), d.getAuteurId(),
                d.getDateCreation());
    }
}

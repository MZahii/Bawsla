package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.IndexCours;
import tn.esprit.bawsla.forum.entity.enums.StatutIndex;

/** État de l'index RAG d'un cours (studio formateur). */
public record IndexCoursResponse(Long coursId, StatutIndex statut, int nbPassages, String modeleEmbedding,
                                 LocalDateTime indexeLe, boolean sourceDemo, String erreur) {

    public static IndexCoursResponse from(IndexCours i) {
        return new IndexCoursResponse(i.getCoursId(), i.getStatut(), i.getNbPassages(), i.getModeleEmbedding(),
                i.getIndexeLe(), i.isSourceDemo(), i.getErreur());
    }
}

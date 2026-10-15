package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;

/** Fil complet : question, réponses triées, commentaires, état de l'utilisateur et droits. */
public record DiscussionDetail(Long id, Long coursId, String coursTitre, Long chapitreId, Integer page,
                               TypeDiscussion type, String titre, String contenu, StatutDiscussion statut,
                               boolean epinglee, boolean verrouillee, boolean masquee, boolean faq,
                               Long reponseAccepteeId, Long fusionneeDansId, int score, int monVote,
                               int nbReponses, int nbVues, int nbAbonnes, List<String> etiquettes,
                               AuteurResponse auteur, LocalDateTime creeLe, LocalDateTime modifieLe,
                               LocalDateTime derniereActiviteLe, boolean suivie, boolean favori,
                               DroitsResponse droits, List<ReponseResponse> reponses,
                               List<CommentaireResponse> commentaires, List<PieceJointeResponse> piecesJointes) {
}

package tn.esprit.bawsla.forum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.CommentaireRequest;
import tn.esprit.bawsla.forum.dto.CommentaireResponse;
import tn.esprit.bawsla.forum.dto.ModerationActionRequest;
import tn.esprit.bawsla.forum.dto.ReactionsResponse;
import tn.esprit.bawsla.forum.dto.ReponseRequest;
import tn.esprit.bawsla.forum.dto.ReponseResponse;
import tn.esprit.bawsla.forum.dto.VoteRequest;
import tn.esprit.bawsla.forum.dto.VoteResponse;
import tn.esprit.bawsla.forum.entity.enums.TypeReaction;
import tn.esprit.bawsla.forum.service.CommentaireService;
import tn.esprit.bawsla.forum.service.ModerationFilService;
import tn.esprit.bawsla.forum.service.ReponseService;
import tn.esprit.bawsla.forum.service.VoteService;

/** Réponses, commentaires, votes et réactions. */
@RestController
@RequestMapping("/api/forum")
public class ReponseController {

    private final ReponseService reponses;
    private final CommentaireService commentaires;
    private final VoteService votes;
    private final ModerationFilService moderation;

    public ReponseController(ReponseService reponses, CommentaireService commentaires, VoteService votes,
                             ModerationFilService moderation) {
        this.reponses = reponses;
        this.commentaires = commentaires;
        this.votes = votes;
        this.moderation = moderation;
    }

    // ---------------------------------------------------------------- Réponses

    @PostMapping("/discussions/{id}/reponses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReponseResponse> repondre(@PathVariable Long id, @Valid @RequestBody ReponseRequest req,
                                                 CurrentUser user) {
        return ApiResponse.ok(reponses.repondre(id, req, user), "Réponse publiée");
    }

    @PutMapping("/reponses/{id}")
    public ApiResponse<ReponseResponse> modifier(@PathVariable Long id, @Valid @RequestBody ReponseRequest req,
                                                 CurrentUser user) {
        return ApiResponse.ok(reponses.modifier(id, req, user), "Réponse modifiée");
    }

    @DeleteMapping("/reponses/{id}")
    public ApiResponse<Void> supprimer(@PathVariable Long id, CurrentUser user) {
        reponses.supprimer(id, user);
        return ApiResponse.ok(null, "Réponse supprimée");
    }

    @PostMapping("/reponses/{id}/approbation")
    public ApiResponse<ReponseResponse> approuver(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(reponses.approuver(id, true, user), "Réponse approuvée");
    }

    @DeleteMapping("/reponses/{id}/approbation")
    public ApiResponse<ReponseResponse> desapprouver(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(reponses.approuver(id, false, user), "Approbation retirée");
    }

    @PostMapping("/reponses/{id}/masquer")
    public ApiResponse<Void> masquerReponse(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) ModerationActionRequest req,
                                            CurrentUser user) {
        moderation.masquerReponse(id, true, DiscussionController.motif(req), user);
        return ApiResponse.ok(null, "Réponse masquée");
    }

    @PostMapping("/reponses/{id}/retablir")
    public ApiResponse<Void> retablirReponse(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) ModerationActionRequest req,
                                             CurrentUser user) {
        moderation.masquerReponse(id, false, DiscussionController.motif(req), user);
        return ApiResponse.ok(null, "Réponse rétablie");
    }

    // ---------------------------------------------------------------- Commentaires

    @PostMapping("/discussions/{id}/commentaires")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentaireResponse> commenterDiscussion(@PathVariable Long id,
                                                               @Valid @RequestBody CommentaireRequest req,
                                                               CurrentUser user) {
        return ApiResponse.ok(commentaires.commenterDiscussion(id, req, user), "Commentaire publié");
    }

    @PostMapping("/reponses/{id}/commentaires")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentaireResponse> commenterReponse(@PathVariable Long id,
                                                            @Valid @RequestBody CommentaireRequest req,
                                                            CurrentUser user) {
        return ApiResponse.ok(commentaires.commenterReponse(id, req, user), "Commentaire publié");
    }

    @PutMapping("/commentaires/{id}")
    public ApiResponse<CommentaireResponse> modifierCommentaire(@PathVariable Long id,
                                                               @Valid @RequestBody CommentaireRequest req,
                                                               CurrentUser user) {
        return ApiResponse.ok(commentaires.modifier(id, req, user), "Commentaire modifié");
    }

    @DeleteMapping("/commentaires/{id}")
    public ApiResponse<Void> supprimerCommentaire(@PathVariable Long id, CurrentUser user) {
        commentaires.supprimer(id, user);
        return ApiResponse.ok(null, "Commentaire supprimé");
    }

    @PostMapping("/commentaires/{id}/masquer")
    public ApiResponse<Void> masquerCommentaire(@PathVariable Long id,
                                                @Valid @RequestBody(required = false) ModerationActionRequest req,
                                                CurrentUser user) {
        moderation.masquerCommentaire(id, true, DiscussionController.motif(req), user);
        return ApiResponse.ok(null, "Commentaire masqué");
    }

    @PostMapping("/commentaires/{id}/retablir")
    public ApiResponse<Void> retablirCommentaire(@PathVariable Long id,
                                                 @Valid @RequestBody(required = false) ModerationActionRequest req,
                                                 CurrentUser user) {
        moderation.masquerCommentaire(id, false, DiscussionController.motif(req), user);
        return ApiResponse.ok(null, "Commentaire rétabli");
    }

    // ---------------------------------------------------------------- Votes et réactions

    @PutMapping("/votes")
    public ApiResponse<VoteResponse> voter(@Valid @RequestBody VoteRequest req, CurrentUser user) {
        return ApiResponse.ok(votes.voter(req, user));
    }

    @PutMapping("/reponses/{id}/reactions/{type}")
    public ApiResponse<ReactionsResponse> reagir(@PathVariable Long id, @PathVariable TypeReaction type,
                                                 CurrentUser user) {
        return ApiResponse.ok(votes.reagir(id, type, true, user));
    }

    @DeleteMapping("/reponses/{id}/reactions/{type}")
    public ApiResponse<ReactionsResponse> retirerReaction(@PathVariable Long id, @PathVariable TypeReaction type,
                                                          CurrentUser user) {
        return ApiResponse.ok(votes.reagir(id, type, false, user));
    }
}

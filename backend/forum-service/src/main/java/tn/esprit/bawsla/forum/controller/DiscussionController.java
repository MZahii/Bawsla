package tn.esprit.bawsla.forum.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.DeplacerRequest;
import tn.esprit.bawsla.forum.dto.DiscussionDetail;
import tn.esprit.bawsla.forum.dto.DiscussionModificationRequest;
import tn.esprit.bawsla.forum.dto.DiscussionRequest;
import tn.esprit.bawsla.forum.dto.DiscussionResume;
import tn.esprit.bawsla.forum.dto.EtiquettesRequest;
import tn.esprit.bawsla.forum.dto.FusionnerRequest;
import tn.esprit.bawsla.forum.dto.ModerationActionRequest;
import tn.esprit.bawsla.forum.dto.MotifRequest;
import tn.esprit.bawsla.forum.dto.PageResponse;
import tn.esprit.bawsla.forum.dto.VersionResponse;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.service.DiscussionService;
import tn.esprit.bawsla.forum.service.DiscussionService.Filtres;
import tn.esprit.bawsla.forum.service.ModerationFilService;

@RestController
@RequestMapping("/api/forum/discussions")
public class DiscussionController {

    private final DiscussionService discussions;
    private final ModerationFilService moderation;

    public DiscussionController(DiscussionService discussions, ModerationFilService moderation) {
        this.discussions = discussions;
        this.moderation = moderation;
    }

    @GetMapping
    public ApiResponse<PageResponse<DiscussionResume>> lister(
            @RequestParam(required = false) Long coursId, @RequestParam(required = false) Long chapitreId,
            @RequestParam(required = false) TypeDiscussion type, @RequestParam(required = false) String statut,
            @RequestParam(required = false) String etiquette, @RequestParam(required = false) String filtre,
            @RequestParam(required = false) String tri, @RequestParam(required = false) String q,
            @RequestParam(required = false) String mode, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer taille, CurrentUser user) {
        Filtres filtres = new Filtres(coursId, chapitreId, type, statut, etiquette, filtre, tri, q, mode, page, taille);
        return ApiResponse.ok(discussions.lister(filtres, user));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiscussionDetail> ouvrir(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(discussions.ouvrir(id, user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiscussionDetail> publier(@Valid @RequestBody DiscussionRequest request, CurrentUser user) {
        return ApiResponse.ok(discussions.publier(request, user), "Discussion publiée");
    }

    @PutMapping("/{id}")
    public ApiResponse<DiscussionDetail> modifier(@PathVariable Long id,
                                                  @Valid @RequestBody DiscussionModificationRequest request,
                                                  CurrentUser user) {
        return ApiResponse.ok(discussions.modifier(id, request, user), "Discussion modifiée");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> supprimer(@PathVariable Long id, CurrentUser user) {
        discussions.supprimer(id, user);
        return ApiResponse.ok(null, "Discussion supprimée");
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<VersionResponse>> versions(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(discussions.versions(id, user));
    }

    // ---------------------------------------------------------------- Meilleure réponse

    @PostMapping("/{id}/accepter/{reponseId}")
    public ApiResponse<DiscussionDetail> accepter(@PathVariable Long id, @PathVariable Long reponseId,
                                                  CurrentUser user) {
        return ApiResponse.ok(discussions.accepter(id, reponseId, user), "Meilleure réponse choisie");
    }

    @DeleteMapping("/{id}/accepter")
    public ApiResponse<DiscussionDetail> retirerAcceptation(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(discussions.retirerAcceptation(id, user), "Meilleure réponse retirée");
    }

    // ---------------------------------------------------------------- Modération (formateur du cours ou admin)

    @PostMapping("/{id}/epingler")
    public ApiResponse<DiscussionDetail> epingler(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(moderation.epingler(id, true, user), "Discussion épinglée");
    }

    @DeleteMapping("/{id}/epingler")
    public ApiResponse<DiscussionDetail> desepingler(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(moderation.epingler(id, false, user), "Discussion désépinglée");
    }

    @PostMapping("/{id}/verrouiller")
    public ApiResponse<DiscussionDetail> verrouiller(@PathVariable Long id,
                                                     @Valid @RequestBody(required = false) ModerationActionRequest req,
                                                     CurrentUser user) {
        return ApiResponse.ok(moderation.verrouiller(id, true, motif(req), user), "Discussion verrouillée");
    }

    @DeleteMapping("/{id}/verrouiller")
    public ApiResponse<DiscussionDetail> deverrouiller(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(moderation.verrouiller(id, false, null, user), "Discussion déverrouillée");
    }

    @PostMapping("/{id}/masquer")
    public ApiResponse<DiscussionDetail> masquer(@PathVariable Long id,
                                                 @Valid @RequestBody MotifRequest req,
                                                 CurrentUser user) {
        return ApiResponse.ok(moderation.masquerDiscussion(id, true, req.motif(), user), "Discussion masquée");
    }

    @PostMapping("/{id}/retablir")
    public ApiResponse<DiscussionDetail> retablir(@PathVariable Long id,
                                                  @Valid @RequestBody MotifRequest req,
                                                  CurrentUser user) {
        return ApiResponse.ok(moderation.masquerDiscussion(id, false, req.motif(), user), "Discussion rétablie");
    }

    @PostMapping("/{id}/deplacer")
    public ApiResponse<DiscussionDetail> deplacer(@PathVariable Long id, @Valid @RequestBody DeplacerRequest req,
                                                  CurrentUser user) {
        return ApiResponse.ok(moderation.deplacer(id, req, user), "Discussion déplacée");
    }

    @PostMapping("/{id}/fusionner")
    public ApiResponse<DiscussionDetail> fusionner(@PathVariable Long id, @Valid @RequestBody FusionnerRequest req,
                                                   CurrentUser user) {
        return ApiResponse.ok(moderation.fusionner(id, req, user), "Discussion fusionnée");
    }

    @PutMapping("/{id}/etiquettes")
    public ApiResponse<DiscussionDetail> etiquettes(@PathVariable Long id, @Valid @RequestBody EtiquettesRequest req,
                                                    CurrentUser user) {
        return ApiResponse.ok(moderation.modifierEtiquettes(id, req, user), "Étiquettes modifiées");
    }

    static String motif(ModerationActionRequest req) {
        return req == null ? null : req.motif();
    }
}

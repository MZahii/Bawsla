package tn.esprit.bawsla.forum.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.AdminStatistiquesResponse;
import tn.esprit.bawsla.forum.dto.AmeliorerRequest;
import tn.esprit.bawsla.forum.dto.AmeliorerResponse;
import tn.esprit.bawsla.forum.dto.ApprenantBloque;
import tn.esprit.bawsla.forum.dto.DiscussionDetail;
import tn.esprit.bawsla.forum.dto.FaqRequest;
import tn.esprit.bawsla.forum.dto.ResumeResponse;
import tn.esprit.bawsla.forum.dto.SupervisionResponse;
import tn.esprit.bawsla.forum.dto.TendancesResponse;
import tn.esprit.bawsla.forum.service.AidesService;
import tn.esprit.bawsla.forum.service.SupervisionService;
import tn.esprit.bawsla.forum.service.TendancesService;

/** Résumé « En bref », aide à la rédaction, tendances et outils du studio, statistiques et supervision admin. */
@RestController
@RequestMapping("/api/forum")
public class TendancesController {

    private final AidesService aides;
    private final TendancesService tendances;
    private final SupervisionService supervision;

    public TendancesController(AidesService aides, TendancesService tendances, SupervisionService supervision) {
        this.aides = aides;
        this.tendances = tendances;
        this.supervision = supervision;
    }

    /** data absent si le fil compte 6 messages ou moins, ou si aucun résumé n'a pu être produit. */
    @GetMapping("/discussions/{id}/resume")
    public ApiResponse<ResumeResponse> resume(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(aides.resume(id, user));
    }

    @PostMapping("/composer/ameliorer")
    public ApiResponse<AmeliorerResponse> ameliorer(@Valid @RequestBody AmeliorerRequest req, CurrentUser user) {
        return ApiResponse.ok(aides.ameliorer(req, user));
    }

    @GetMapping("/tendances")
    public ApiResponse<TendancesResponse> tendances(@RequestParam(required = false) Long coursId, CurrentUser user) {
        return ApiResponse.ok(tendances.tendances(coursId, user));
    }

    @GetMapping("/cours/{coursId}/apprenants-bloques")
    public ApiResponse<List<ApprenantBloque>> apprenantsBloques(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(tendances.apprenantsBloques(coursId, user));
    }

    @PostMapping("/cours/{coursId}/faq")
    public ApiResponse<DiscussionDetail> faq(@PathVariable Long coursId, @Valid @RequestBody FaqRequest req,
                                             CurrentUser user) {
        return ApiResponse.ok(tendances.ajouterFaq(coursId, req.discussionId(), user), "Ajouté à la FAQ du cours");
    }

    @GetMapping("/admin/statistiques")
    public ApiResponse<AdminStatistiquesResponse> statistiques(@RequestParam(required = false) Integer periode,
                                                               CurrentUser user) {
        return ApiResponse.ok(supervision.statistiques(periode, user));
    }

    @GetMapping("/admin/vigie/supervision")
    public ApiResponse<SupervisionResponse> supervision(@RequestParam(required = false) Integer periode,
                                                        CurrentUser user) {
        return ApiResponse.ok(supervision.supervision(periode, user));
    }
}

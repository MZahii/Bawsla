package tn.esprit.bawsla.forum.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.IndexCoursResponse;
import tn.esprit.bawsla.forum.dto.PourToiResponse;
import tn.esprit.bawsla.forum.dto.SimilaireResponse;
import tn.esprit.bawsla.forum.dto.SimilairesRequest;
import tn.esprit.bawsla.forum.dto.SimilairesResponse;
import tn.esprit.bawsla.forum.service.IndexationService;
import tn.esprit.bawsla.forum.service.RechercheService;

/** Recherche sémantique : questions similaires en direct, discussions liées, « Pour toi », index des cours. */
@RestController
@RequestMapping("/api/forum")
public class RechercheController {

    private final RechercheService recherche;
    private final IndexationService indexation;

    public RechercheController(RechercheService recherche, IndexationService indexation) {
        this.recherche = recherche;
        this.indexation = indexation;
    }

    /** Pendant la saisie du titre (15 caractères au moins, anti-rebond côté front). */
    @PostMapping("/discussions/similaires")
    public ApiResponse<SimilairesResponse> similaires(@Valid @RequestBody SimilairesRequest req, CurrentUser user) {
        return ApiResponse.ok(recherche.similaires(req, user));
    }

    @GetMapping("/discussions/{id}/liees")
    public ApiResponse<List<SimilaireResponse>> liees(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(recherche.liees(id, user));
    }

    @GetMapping("/pour-moi")
    public ApiResponse<PourToiResponse> pourMoi(CurrentUser user) {
        return ApiResponse.ok(recherche.pourMoi(user));
    }

    // ---------------------------------------------------------------- Index des cours (formateur du cours ou admin)

    @GetMapping("/index/{coursId}")
    public ApiResponse<IndexCoursResponse> etat(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(indexation.etat(coursId, user));
    }

    @PostMapping("/index/{coursId}/reindexer")
    public ApiResponse<IndexCoursResponse> reindexer(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(indexation.reindexer(coursId, user), "Réindexation lancée");
    }

    @PostMapping("/admin/index/reindexer-tout")
    public ApiResponse<Integer> reindexerTout(CurrentUser user) {
        return ApiResponse.ok(indexation.reindexerTout(user), "Réindexation de tous les cours lancée");
    }
}

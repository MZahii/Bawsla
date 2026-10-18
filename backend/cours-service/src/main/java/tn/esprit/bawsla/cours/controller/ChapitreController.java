package tn.esprit.bawsla.cours.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.ChapitreRequest;
import tn.esprit.bawsla.cours.dto.ChapitreResponse;
import tn.esprit.bawsla.cours.dto.OrdreRequest;
import tn.esprit.bawsla.cours.service.ChapitreService;

@RestController
@RequestMapping("/api/cours/{coursId}/chapitres")
public class ChapitreController {

    private final ChapitreService chapitreService;

    public ChapitreController(ChapitreService chapitreService) {
        this.chapitreService = chapitreService;
    }

    /** Programme du cours : lisible par un visiteur du catalogue public si le cours est publié. */
    @GetMapping
    public ApiResponse<List<ChapitreResponse>> lister(@PathVariable Long coursId,
                                                      @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                      @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ApiResponse.ok(chapitreService.lister(coursId, CoursController.appelant(userId, role)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChapitreResponse> creer(@PathVariable Long coursId, @Valid @RequestBody ChapitreRequest request,
                                               CurrentUser user) {
        return ApiResponse.ok(chapitreService.creer(coursId, request, user), "Chapitre ajouté");
    }

    @PutMapping("/{chapitreId}")
    public ApiResponse<ChapitreResponse> modifier(@PathVariable Long coursId, @PathVariable Long chapitreId,
                                                  @Valid @RequestBody ChapitreRequest request, CurrentUser user) {
        return ApiResponse.ok(chapitreService.modifier(coursId, chapitreId, request, user), "Chapitre modifié");
    }

    @DeleteMapping("/{chapitreId}")
    public ApiResponse<Void> supprimer(@PathVariable Long coursId, @PathVariable Long chapitreId, CurrentUser user) {
        chapitreService.supprimer(coursId, chapitreId, user);
        return ApiResponse.ok(null, "Chapitre supprimé");
    }

    @PatchMapping("/ordre")
    public ApiResponse<List<ChapitreResponse>> reordonner(@PathVariable Long coursId,
                                                          @Valid @RequestBody OrdreRequest request, CurrentUser user) {
        return ApiResponse.ok(chapitreService.reordonner(coursId, request, user), "Ordre enregistré");
    }
}

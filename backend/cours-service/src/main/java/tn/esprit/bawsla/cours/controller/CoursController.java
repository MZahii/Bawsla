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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CoursRequest;
import tn.esprit.bawsla.cours.dto.CoursResponse;
import tn.esprit.bawsla.cours.dto.CoursTexteResponse;
import tn.esprit.bawsla.cours.dto.StatutRequest;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.service.CoursService;

@RestController
@RequestMapping("/api/cours")
public class CoursController {

    private final CoursService coursService;

    public CoursController(CoursService coursService) {
        this.coursService = coursService;
    }

    /**
     * Visiteur (catalogue public, sans en-têtes) et apprenant : cours publiés. Formateur : publiés et les siens.
     * ADMIN : tout.
     */
    @GetMapping
    public ApiResponse<List<CoursResponse>> findAll(@RequestParam(required = false) String categorie,
                                                    @RequestParam(required = false) Niveau niveau,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                    @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ApiResponse.ok(coursService.findAll(categorie, niveau, appelant(userId, role)));
    }

    /** Contrat inter-modules (utilisé par quiz-service via Feign). Lisible par un visiteur si le cours est publié. */
    @GetMapping("/{id}")
    public ApiResponse<CoursResponse> findById(@PathVariable Long id,
                                               @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ApiResponse.ok(coursService.findById(id, appelant(userId, role)));
    }

    /** Appelant identifié par la gateway, ou null pour un visiteur du catalogue public. */
    static CurrentUser appelant(Long userId, String role) {
        return userId == null || role == null ? null : new CurrentUser(userId, role);
    }

    /** Contrat inter-modules (IA et RAG du Forum) : texte du PDF, emails et téléphones masqués. */
    @GetMapping("/{id}/texte")
    public ApiResponse<CoursTexteResponse> getTexte(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(coursService.getTexte(id, user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoursResponse> create(@Valid @RequestBody CoursRequest request, CurrentUser user) {
        return ApiResponse.ok(coursService.create(request, user), "Cours créé");
    }

    @PutMapping("/{id}")
    public ApiResponse<CoursResponse> update(@PathVariable Long id, @Valid @RequestBody CoursRequest request,
                                             CurrentUser user) {
        return ApiResponse.ok(coursService.update(id, request, user), "Cours modifié");
    }

    /** Publier, dépublier ou archiver (propriétaire ou ADMIN). Publier exige au moins un chapitre. */
    @PatchMapping("/{id}/statut")
    public ApiResponse<CoursResponse> changerStatut(@PathVariable Long id, @Valid @RequestBody StatutRequest request,
                                                    CurrentUser user) {
        return ApiResponse.ok(coursService.changerStatut(id, request, user), "Statut modifié");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, CurrentUser user) {
        coursService.delete(id, user);
        return ApiResponse.ok(null, "Cours supprimé");
    }
}

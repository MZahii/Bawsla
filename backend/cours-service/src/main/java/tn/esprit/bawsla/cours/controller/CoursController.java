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

    /** Apprenant : cours publiés. Formateur : publiés et les siens. ADMIN : tout. */
    @GetMapping
    public ApiResponse<List<CoursResponse>> findAll(@RequestParam(required = false) String categorie,
                                                    @RequestParam(required = false) Niveau niveau,
                                                    CurrentUser user) {
        return ApiResponse.ok(coursService.findAll(categorie, niveau, user));
    }

    /** Contrat inter-modules (utilisé par quiz-service via Feign). */
    @GetMapping("/{id}")
    public ApiResponse<CoursResponse> findById(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(coursService.findById(id, user));
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

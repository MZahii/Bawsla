package tn.esprit.bawsla.cours.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.ResumeLocalResponse;
import tn.esprit.bawsla.cours.dto.TraitementResponse;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;
import tn.esprit.bawsla.cours.service.ResumeLocalService;

/**
 * Résumé automatique LOCAL (modèle Hugging Face sur CPU ou phrases clés, sans clé API). Le {@code Authorization} de l'utilisateur est lu ICI,
 * avant tout passage en asynchrone, puis transmis à l'IA par la gateway.
 */
@RestController
@RequestMapping("/api/cours/{coursId}/resume-local")
public class ResumeLocalController {

    private final ResumeLocalService service;

    public ResumeLocalController(ResumeLocalService service) {
        this.service = service;
    }

    /** Lance le résumé en arrière-plan (propriétaire ou ADMIN). Suivre l'avancement avec GET /traitement. */
    @PostMapping("/generer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TraitementResponse> generer(@PathVariable Long coursId, @RequestParam(defaultValue = "moyenne") String longueur,
                                                   @RequestParam(defaultValue = "auto") String mode, CurrentUser user,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.generer(coursId, longueur, mode, user, authorization), "Résumé lancé");
    }

    @GetMapping("/traitement")
    public ApiResponse<TraitementResponse> traitement(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(service.traitement(coursId, user));
    }

    /** Apprenant : dernière version VALIDE. Formateur propriétaire et ADMIN : la plus récente. Sans résumé : 200 sans {@code data}. */
    @GetMapping
    public ApiResponse<ResumeLocalResponse> resume(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(service.resume(coursId, user));
    }

    @PatchMapping("/valider")
    public ApiResponse<ResumeLocalResponse> valider(@PathVariable Long coursId, CurrentUser user,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.valider(coursId, user, authorization), "Résumé validé");
    }

    /** Audio du résumé (même règle de visibilité que le texte). */
    @GetMapping("/audio")
    public ResponseEntity<Resource> audio(@PathVariable Long coursId, CurrentUser user) {
        Fichier f = service.audio(coursId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .header("X-Content-Type-Options", "nosniff")
                .body(f.ressource());
    }
}

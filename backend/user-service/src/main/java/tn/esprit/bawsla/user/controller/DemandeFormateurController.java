package tn.esprit.bawsla.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.DemandeFormateurDto;
import tn.esprit.bawsla.user.dto.DemandeFormateurRequest;
import tn.esprit.bawsla.user.dto.RefusRequest;
import tn.esprit.bawsla.user.service.DemandeFormateurService;

/** Demandes pour devenir formateur : côté demandeur (/me) et côté ADMIN. */
@RestController
@RequestMapping("/api/users")
public class DemandeFormateurController {

    private final DemandeFormateurService service;

    public DemandeFormateurController(DemandeFormateurService service) {
        this.service = service;
    }

    /** Ma dernière demande ; data absent si je n'en ai jamais fait. */
    @GetMapping("/me/demande-formateur")
    public ApiResponse<DemandeFormateurDto> maDemande(CurrentUser user) {
        return ApiResponse.ok(service.maDemande(user).orElse(null));
    }

    @PostMapping("/me/demande-formateur")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DemandeFormateurDto> soumettre(@Valid @RequestBody DemandeFormateurRequest request,
                                                      CurrentUser user) {
        return ApiResponse.ok(service.soumettre(user, request), "Demande envoyée");
    }

    /** ADMIN uniquement. */
    @GetMapping("/demandes-formateur")
    public ApiResponse<List<DemandeFormateurDto>> lister(CurrentUser user) {
        return ApiResponse.ok(service.lister(user));
    }

    /** ADMIN uniquement. */
    @PostMapping("/demandes-formateur/{id}/accepter")
    public ApiResponse<DemandeFormateurDto> accepter(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(service.accepter(user, id), "Demande acceptée");
    }

    /** ADMIN uniquement. */
    @PostMapping("/demandes-formateur/{id}/refuser")
    public ApiResponse<DemandeFormateurDto> refuser(@PathVariable Long id, @Valid @RequestBody RefusRequest request,
                                                    CurrentUser user) {
        return ApiResponse.ok(service.refuser(user, id, request.motif()), "Demande refusée");
    }
}

package tn.esprit.bawsla.cours.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import tn.esprit.bawsla.cours.dto.FicheRequest;
import tn.esprit.bawsla.cours.dto.FicheResponse;
import tn.esprit.bawsla.cours.dto.TraitementResponse;
import tn.esprit.bawsla.cours.service.FicheService;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;

/**
 * Fiche de révision générée par l'IA : lancement et suivi (formateur), lecture, correction, validation et audio.
 * Le {@code Authorization} de l'utilisateur est transmis à l'IA, qui est appelée par la gateway.
 */
@RestController
@RequestMapping("/api/cours/{coursId}")
public class FicheController {

    private final FicheService ficheService;

    public FicheController(FicheService ficheService) {
        this.ficheService = ficheService;
    }

    /** Lance la génération en arrière-plan (propriétaire ou ADMIN) : suivre l'avancement avec GET /traitement. */
    @PostMapping("/fiche/generer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TraitementResponse> generer(@PathVariable Long coursId, CurrentUser user,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(ficheService.lancerGeneration(coursId, user, authorization), "Génération lancée");
    }

    @GetMapping("/traitement")
    public ApiResponse<TraitementResponse> traitement(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(ficheService.traitement(coursId, user));
    }

    /**
     * Apprenant : dernière fiche VALIDEE. Formateur propriétaire et ADMIN : fiche la plus récente.
     * Sans fiche à montrer : 200 sans {@code data} (et non 404, qui reste réservé à un cours introuvable).
     */
    @GetMapping("/fiche")
    public ApiResponse<FicheResponse> fiche(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(ficheService.fiche(coursId, user));
    }

    @PutMapping("/fiche")
    public ApiResponse<FicheResponse> corriger(@PathVariable Long coursId, @Valid @RequestBody FicheRequest requete,
                                               CurrentUser user,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(ficheService.corriger(coursId, requete, user, authorization), "Fiche enregistrée");
    }

    @PatchMapping("/fiche/valider")
    public ApiResponse<FicheResponse> valider(@PathVariable Long coursId, CurrentUser user,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(ficheService.valider(coursId, user, authorization), "Fiche validée");
    }

    /** MP3 du résumé (lecture en continu, avec reprise par plages). Même règle de visibilité que la fiche. */
    @GetMapping("/audio")
    public ResponseEntity<Resource> audio(@PathVariable Long coursId, CurrentUser user) {
        Fichier f = ficheService.audio(coursId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .header("X-Content-Type-Options", "nosniff")
                .body(f.ressource());
    }
}

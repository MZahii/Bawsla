package tn.esprit.bawsla.cours.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CorrectionExplicationRequest;
import tn.esprit.bawsla.cours.dto.DisponibiliteExplicationResponse;
import tn.esprit.bawsla.cours.dto.ExplicationResponse;
import tn.esprit.bawsla.cours.dto.TraitementResponse;
import tn.esprit.bawsla.cours.entity.LangueExplication;
import tn.esprit.bawsla.cours.entity.ModeExplication;
import tn.esprit.bawsla.cours.service.ExplicationService;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;

/**
 * Explication orale par l'IA (script, aperçu audio, correction, validation). Toutes les routes prennent {@code langue} (fr ou en,
 * fr par défaut) et {@code mode} (monologue ou dialogue, monologue par défaut). Le {@code Authorization} de l'utilisateur est lu ICI,
 * avant tout passage en asynchrone, puis transmis à l'IA, qui est appelée par la gateway.
 */
@RestController
@RequestMapping("/api/cours/{coursId}/explication")
public class ExplicationController {

    private final ExplicationService service;

    public ExplicationController(ExplicationService service) {
        this.service = service;
    }

    /** Lance la génération en arrière-plan (propriétaire ou ADMIN). Suivre l'avancement avec GET /traitement. */
    @PostMapping("/generer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TraitementResponse> generer(@PathVariable Long coursId, @RequestParam(defaultValue = "fr") String langue,
                                                   @RequestParam(defaultValue = "monologue") String mode,
                                                   @RequestParam(defaultValue = "4") int duree, CurrentUser user,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.lancerGeneration(coursId, LangueExplication.depuis(langue), ModeExplication.depuis(mode), duree,
                user, authorization), "Génération lancée");
    }

    /** « Écouter un aperçu » : met la version la plus récente en voix, à la demande du formateur. */
    @PostMapping("/apercu")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TraitementResponse> apercu(@PathVariable Long coursId, @RequestParam(defaultValue = "fr") String langue,
                                                  @RequestParam(defaultValue = "monologue") String mode, CurrentUser user,
                                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.apercu(coursId, LangueExplication.depuis(langue), ModeExplication.depuis(mode), user, authorization),
                "Aperçu en préparation");
    }

    @GetMapping("/traitement")
    public ApiResponse<TraitementResponse> traitement(@PathVariable Long coursId, @RequestParam(defaultValue = "fr") String langue,
                                                      @RequestParam(defaultValue = "monologue") String mode, CurrentUser user) {
        return ApiResponse.ok(service.traitement(coursId, LangueExplication.depuis(langue), ModeExplication.depuis(mode), user));
    }

    /** Les combinaisons langue x mode qui existent (validées pour l'apprenant). */
    @GetMapping("/disponibles")
    public ApiResponse<List<DisponibiliteExplicationResponse>> disponibles(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(service.disponibles(coursId, user));
    }

    /**
     * Apprenant : dernière version VALIDEE. Formateur propriétaire et ADMIN : version la plus récente.
     * Sans explication à montrer : 200 sans {@code data} (404 reste réservé à un cours introuvable).
     */
    @GetMapping
    public ApiResponse<ExplicationResponse> explication(@PathVariable Long coursId, @RequestParam(defaultValue = "fr") String langue,
                                                        @RequestParam(defaultValue = "monologue") String mode, CurrentUser user) {
        return ApiResponse.ok(service.explication(coursId, LangueExplication.depuis(langue), ModeExplication.depuis(mode), user));
    }

    @PutMapping
    public ApiResponse<ExplicationResponse> corriger(@PathVariable Long coursId, @RequestParam(defaultValue = "fr") String langue,
                                                     @RequestParam(defaultValue = "monologue") String mode,
                                                     @Valid @RequestBody CorrectionExplicationRequest requete, CurrentUser user,
                                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.corriger(coursId, LangueExplication.depuis(langue), ModeExplication.depuis(mode), requete, user,
                authorization), "Explication enregistrée");
    }

    @PatchMapping("/valider")
    public ApiResponse<ExplicationResponse> valider(@PathVariable Long coursId, @RequestParam(defaultValue = "fr") String langue,
                                                    @RequestParam(defaultValue = "monologue") String mode, CurrentUser user,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.valider(coursId, LangueExplication.depuis(langue), ModeExplication.depuis(mode), user, authorization),
                "Explication validée");
    }

    /** Audio en lecture continue (reprise par plages). Même règle de visibilité que l'explication. */
    @GetMapping("/audio")
    public ResponseEntity<Resource> audio(@PathVariable Long coursId, @RequestParam(defaultValue = "fr") String langue,
                                          @RequestParam(defaultValue = "monologue") String mode, CurrentUser user) {
        Fichier f = service.audio(coursId, LangueExplication.depuis(langue), ModeExplication.depuis(mode), user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .header("X-Content-Type-Options", "nosniff")
                .body(f.ressource());
    }
}

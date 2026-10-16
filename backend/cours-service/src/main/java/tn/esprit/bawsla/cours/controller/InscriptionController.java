package tn.esprit.bawsla.cours.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.InscriptionResponse;
import tn.esprit.bawsla.cours.dto.InscriptionResumeResponse;
import tn.esprit.bawsla.cours.dto.ProgressionResponse;
import tn.esprit.bawsla.cours.service.InscriptionService;

/**
 * Inscriptions des apprenants (actions de l'apprenant) et lecture pour le module User (contrat 7).
 * Les chemins fixes (/inscriptions, /mes-inscriptions) l'emportent sur /api/cours/{id} : les routes existantes ne changent pas.
 */
@RestController
@RequestMapping("/api/cours")
public class InscriptionController {

    private final InscriptionService inscriptionService;

    public InscriptionController(InscriptionService inscriptionService) {
        this.inscriptionService = inscriptionService;
    }

    /** 201 à la création, 200 avec l'inscription existante au deuxième appel (idempotent). */
    @PostMapping("/{id}/inscriptions")
    public ResponseEntity<ApiResponse<InscriptionResponse>> inscrire(@PathVariable Long id, CurrentUser user) {
        InscriptionService.Inscrite resultat = inscriptionService.inscrire(id, user);
        return ResponseEntity.status(resultat.creee() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(resultat.inscription(), resultat.creee() ? "Inscription enregistrée" : "Déjà inscrit"));
    }

    @DeleteMapping("/{id}/inscriptions")
    public ApiResponse<Void> desinscrire(@PathVariable Long id, CurrentUser user) {
        inscriptionService.desinscrire(id, user);
        return ApiResponse.ok(null, "Inscription supprimée");
    }

    @PostMapping("/{id}/chapitres/{chapitreId}/terminer")
    public ApiResponse<ProgressionResponse> terminer(@PathVariable Long id, @PathVariable Long chapitreId, CurrentUser user) {
        return ApiResponse.ok(inscriptionService.terminerChapitre(id, chapitreId, user));
    }

    @DeleteMapping("/{id}/chapitres/{chapitreId}/terminer")
    public ApiResponse<ProgressionResponse> annuler(@PathVariable Long id, @PathVariable Long chapitreId, CurrentUser user) {
        return ApiResponse.ok(inscriptionService.annulerChapitre(id, chapitreId, user));
    }

    /** Progression de l'apprenant connecté dans ce cours (avec les chapitres terminés) ; 200 sans {@code data} s'il n'est pas inscrit. */
    @GetMapping("/{id}/progression")
    public ApiResponse<ProgressionResponse> maProgression(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(inscriptionService.maProgression(id, user));
    }

    @GetMapping("/mes-inscriptions")
    public ApiResponse<List<InscriptionResponse>> mesInscriptions(CurrentUser user) {
        return ApiResponse.ok(inscriptionService.mesInscriptions(user));
    }

    /** Lot 2 : signal d'activité du lecteur (toutes les 60 s, onglet visible). */
    @PostMapping("/{id}/activite")
    public ApiResponse<InscriptionResponse> activite(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(inscriptionService.signalerActivite(id, user));
    }

    /** Contrat 8 : toutes les inscriptions (ADMIN), pour l'entraînement du filtrage collaboratif du module User. */
    @GetMapping("/inscriptions/toutes")
    public ApiResponse<List<InscriptionResumeResponse>> toutes(CurrentUser user) {
        return ApiResponse.ok(inscriptionService.toutes(user));
    }

    /** Contrat 7 : par apprenant (lui-même, ENSEIGNANT, ADMIN). {@code etudiantId} absent : 400. */
    @GetMapping("/inscriptions")
    public ApiResponse<List<InscriptionResponse>> parEtudiant(@RequestParam(required = false) Long etudiantId, CurrentUser user) {
        return ApiResponse.ok(inscriptionService.parEtudiant(etudiantId, user));
    }

    /** Contrat 7 : par cours (formateur propriétaire, ADMIN). */
    @GetMapping("/{id}/inscriptions")
    public ApiResponse<List<InscriptionResponse>> parCours(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(inscriptionService.parCours(id, user));
    }
}

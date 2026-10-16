package tn.esprit.bawsla.forum.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.DecisionRequest;
import tn.esprit.bawsla.forum.dto.ParametresCoursRequest;
import tn.esprit.bawsla.forum.dto.ParametresCoursResponse;
import tn.esprit.bawsla.forum.dto.SignalementRequest;
import tn.esprit.bawsla.forum.dto.SignalementResponse;
import tn.esprit.bawsla.forum.dto.VerificationRequest;
import tn.esprit.bawsla.forum.dto.VerificationResponse;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;
import tn.esprit.bawsla.forum.service.ModerationAutoService;
import tn.esprit.bawsla.forum.service.ReglagesService;
import tn.esprit.bawsla.forum.service.SignalementService;

/** Signalements, file de modération d'un cours, vérification avant publication, réglages du forum d'un cours. */
@RestController
@RequestMapping("/api/forum")
public class ModerationController {

    private final SignalementService signalements;
    private final ModerationAutoService moderation;
    private final ReglagesService reglages;

    public ModerationController(SignalementService signalements, ModerationAutoService moderation,
                                ReglagesService reglages) {
        this.signalements = signalements;
        this.moderation = moderation;
        this.reglages = reglages;
    }

    @PostMapping("/signalements")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignalementResponse> signaler(@Valid @RequestBody SignalementRequest req, CurrentUser user) {
        return ApiResponse.ok(signalements.signaler(req, user), "Merci, la modération va examiner ce message");
    }

    /** File du formateur du cours (ou de l'admin) ; statut absent = OUVERT, « TOUS » = tous. */
    @GetMapping("/moderation/file")
    public ApiResponse<List<SignalementResponse>> file(@RequestParam Long coursId,
                                                       @RequestParam(required = false) String statut,
                                                       CurrentUser user) {
        return ApiResponse.ok(signalements.fileDuCours(coursId, statut(statut), user));
    }

    @PostMapping("/signalements/{id}/decision")
    public ApiResponse<SignalementResponse> decider(@PathVariable Long id, @Valid @RequestBody DecisionRequest req,
                                                    CurrentUser user) {
        return ApiResponse.ok(signalements.decider(id, req, user), "Décision enregistrée");
    }

    /** Avant publication : données personnelles à signaler à l'auteur (rien n'est enregistré). */
    @PostMapping("/composer/verifier")
    public ApiResponse<VerificationResponse> verifier(@Valid @RequestBody VerificationRequest req, CurrentUser user) {
        user.requireAnyRole(CurrentUser.ADMIN, CurrentUser.ENSEIGNANT, CurrentUser.ETUDIANT);
        return ApiResponse.ok(moderation.verifier(req));
    }

    @GetMapping("/cours/{coursId}/parametres")
    public ApiResponse<ParametresCoursResponse> parametres(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(reglages.cours(coursId, user));
    }

    @PutMapping("/cours/{coursId}/parametres")
    public ApiResponse<ParametresCoursResponse> modifierParametres(@PathVariable Long coursId,
                                                                   @Valid @RequestBody ParametresCoursRequest req,
                                                                   CurrentUser user) {
        return ApiResponse.ok(reglages.modifierCours(coursId, req, user), "Réglages du forum enregistrés");
    }

    static StatutSignalement statut(String statut) {
        if (statut == null || statut.isBlank()) {
            return StatutSignalement.OUVERT;
        }
        if ("TOUS".equalsIgnoreCase(statut)) {
            return null;
        }
        try {
            return StatutSignalement.valueOf(statut.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Statut inconnu : " + statut);
        }
    }
}

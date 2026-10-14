package tn.esprit.bawsla.quiz.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.quiz.common.ApiResponse;
import tn.esprit.bawsla.quiz.common.security.CurrentUser;
import tn.esprit.bawsla.quiz.dto.HistoriqueLigne;
import tn.esprit.bawsla.quiz.dto.NotionRateeDto;
import tn.esprit.bawsla.quiz.dto.PassageResponse;
import tn.esprit.bawsla.quiz.dto.RattrapageRequest;
import tn.esprit.bawsla.quiz.dto.RattrapageResultat;
import tn.esprit.bawsla.quiz.dto.RemediationResponse;
import tn.esprit.bawsla.quiz.dto.TentativeRequest;
import tn.esprit.bawsla.quiz.dto.TentativeResultat;
import tn.esprit.bawsla.quiz.service.QuizService;
import tn.esprit.bawsla.quiz.service.RemediationService;
import tn.esprit.bawsla.quiz.service.TentativeService;

/** Côté apprenant : passer un quiz, voir sa correction, demander la remédiation IA, consulter son historique. */
@RestController
@RequestMapping("/api/quiz")
public class PassageController {

    private final QuizService quizService;
    private final TentativeService tentativeService;
    private final RemediationService remediationService;

    public PassageController(QuizService quizService, TentativeService tentativeService,
                             RemediationService remediationService) {
        this.quizService = quizService;
        this.tentativeService = tentativeService;
        this.remediationService = remediationService;
    }

    /** Questions à passer, sans les bonnes réponses. */
    @GetMapping("/{id}/passage")
    public ApiResponse<PassageResponse> passage(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(quizService.passage(id, user));
    }

    @PostMapping("/{id}/tentatives")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TentativeResultat> soumettre(@PathVariable Long id, @Valid @RequestBody TentativeRequest request,
                                                    CurrentUser user) {
        return ApiResponse.ok(tentativeService.soumettre(id, request, user), "Tentative corrigée");
    }

    @GetMapping("/tentatives/mes")
    public ApiResponse<List<HistoriqueLigne>> historique(CurrentUser user) {
        return ApiResponse.ok(tentativeService.historique(user));
    }

    /** Contrat 3 (module User / ML) : notions ratées d'un apprenant, agrégées par compétence. */
    @GetMapping("/notions-ratees")
    public ApiResponse<List<NotionRateeDto>> notionsRatees(@RequestParam Long etudiantId, CurrentUser user) {
        return ApiResponse.ok(tentativeService.notionsRatees(etudiantId, user));
    }

    /** Explication IA d'une mauvaise réponse ; le jeton de l'utilisateur est relayé à la gateway. */
    @PostMapping("/reponses/{reponseId}/remediation")
    public ApiResponse<RemediationResponse> remedier(@PathVariable Long reponseId,
                                                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                     CurrentUser user) {
        return ApiResponse.ok(remediationService.remedier(reponseId, authorization, user));
    }

    @PostMapping("/remediations/{id}/rattrapage")
    public ApiResponse<RattrapageResultat> rattrapage(@PathVariable Long id, @Valid @RequestBody RattrapageRequest request,
                                                      CurrentUser user) {
        return ApiResponse.ok(remediationService.rattrapage(id, request, user));
    }
}

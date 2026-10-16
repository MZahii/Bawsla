package tn.esprit.bawsla.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.ContenuIaResponse;
import tn.esprit.bawsla.user.dto.SuggestionParcoursRequest;
import tn.esprit.bawsla.user.entity.TypeContenuIa;
import tn.esprit.bawsla.user.service.IaGenerativeService;

/**
 * IA générative du module User (le Machine Learning décide, le LLM explique). GET : le dernier texte gardé, sans
 * appel au LLM ({@code data} absent s'il n'y en a pas). POST : génère, ou renvoie le texte gardé si les données n'ont
 * pas changé. LLM indisponible : 503 avec un message clair.
 */
@RestController
@RequestMapping("/api/users")
public class IaGenerativeController {

    private final IaGenerativeService service;

    public IaGenerativeController(IaGenerativeService service) {
        this.service = service;
    }

    /** Apprenant : le conseil de sa boussole (jamais son risque). */
    @GetMapping("/me/ia/conseil")
    public ApiResponse<ContenuIaResponse> monConseil(CurrentUser user) {
        return ApiResponse.ok(service.dernier(user, TypeContenuIa.CONSEIL, null));
    }

    @PostMapping("/me/ia/conseil")
    public ApiResponse<ContenuIaResponse> genererMonConseil(CurrentUser user,
                                                            @RequestHeader(value = "Authorization", required = false) String jeton) {
        return ApiResponse.ok(service.conseil(user, jeton));
    }

    /** Apprenant : son plan d'étude des 7 prochains jours. */
    @GetMapping("/me/ia/plan")
    public ApiResponse<ContenuIaResponse> monPlan(CurrentUser user) {
        return ApiResponse.ok(service.dernier(user, TypeContenuIa.PLAN, null));
    }

    @PostMapping("/me/ia/plan")
    public ApiResponse<ContenuIaResponse> genererMonPlan(CurrentUser user,
                                                         @RequestHeader(value = "Authorization", required = false) String jeton) {
        return ApiResponse.ok(service.plan(user, jeton));
    }

    /** Apprenant : domaines et objectif suggérés depuis une phrase (rien n'est enregistré). */
    @PostMapping("/me/parcours/suggestion")
    public ApiResponse<JsonNode> suggestionParcours(@Valid @RequestBody SuggestionParcoursRequest request, CurrentUser user,
                                                    @RequestHeader(value = "Authorization", required = false) String jeton) {
        return ApiResponse.ok(service.suggestionParcours(user, request.texte(), jeton));
    }

    /** ENSEIGNANT ou ADMIN : explication du risque d'un apprenant et actions proposées. */
    @GetMapping("/{id}/ia/explication")
    public ApiResponse<ContenuIaResponse> explication(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(service.dernier(user, TypeContenuIa.EXPLICATION_RISQUE, id));
    }

    @PostMapping("/{id}/ia/explication")
    public ApiResponse<ContenuIaResponse> genererExplication(@PathVariable Long id, CurrentUser user,
                                                             @RequestHeader(value = "Authorization", required = false) String jeton) {
        return ApiResponse.ok(service.explication(user, id, jeton));
    }

    /** ENSEIGNANT (ses cours) ou ADMIN (tous) : rapport de la classe, à partir de statistiques agrégées. */
    @GetMapping("/formateur/apprenants/rapport")
    public ApiResponse<ContenuIaResponse> rapport(CurrentUser user) {
        return ApiResponse.ok(service.dernier(user, TypeContenuIa.RAPPORT_COHORTE, null));
    }

    @PostMapping("/formateur/apprenants/rapport")
    public ApiResponse<ContenuIaResponse> genererRapport(CurrentUser user,
                                                         @RequestHeader(value = "Authorization", required = false) String jeton) {
        return ApiResponse.ok(service.rapport(user, jeton));
    }
}

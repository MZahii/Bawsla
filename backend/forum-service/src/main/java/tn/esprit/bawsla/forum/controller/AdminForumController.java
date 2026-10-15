package tn.esprit.bawsla.forum.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.CompteurResponse;
import tn.esprit.bawsla.forum.dto.HistoriqueMembreResponse;
import tn.esprit.bawsla.forum.dto.JournalResponse;
import tn.esprit.bawsla.forum.dto.PageResponse;
import tn.esprit.bawsla.forum.dto.ParametresForumRequest;
import tn.esprit.bawsla.forum.dto.ParametresForumResponse;
import tn.esprit.bawsla.forum.dto.SanctionRequest;
import tn.esprit.bawsla.forum.dto.SanctionResponse;
import tn.esprit.bawsla.forum.dto.SignalementResponse;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.CategorieModeration;
import tn.esprit.bawsla.forum.service.ReglagesService;
import tn.esprit.bawsla.forum.service.SanctionService;
import tn.esprit.bawsla.forum.service.SignalementService;

/** Espace admin du forum (ADMIN uniquement, vérifié dans chaque service) : modération, sanctions, réglages, journal. */
@RestController
@RequestMapping("/api/forum/admin")
public class AdminForumController {

    private final SignalementService signalements;
    private final SanctionService sanctions;
    private final ReglagesService reglages;

    public AdminForumController(SignalementService signalements, SanctionService sanctions,
                                ReglagesService reglages) {
        this.signalements = signalements;
        this.sanctions = sanctions;
        this.reglages = reglages;
    }

    /** File globale ; escalades=true : escalades des formateurs et décisions de l'IA seulement. */
    @GetMapping("/moderation/file")
    public ApiResponse<List<SignalementResponse>> file(@RequestParam(required = false) String statut,
                                                       @RequestParam(required = false) CategorieModeration categorie,
                                                       @RequestParam(defaultValue = "false") boolean escalades,
                                                       CurrentUser user) {
        return ApiResponse.ok(signalements.fileAdmin(ModerationController.statut(statut), categorie, escalades, user));
    }

    /** Nombre de signalements ouverts (compteur du menu admin). */
    @GetMapping("/moderation/compteur")
    public ApiResponse<CompteurResponse> compteur(CurrentUser user) {
        return ApiResponse.ok(new CompteurResponse(signalements.ouverts(user)));
    }

    @GetMapping("/sanctions")
    public ApiResponse<List<SanctionResponse>> sanctions(@RequestParam(defaultValue = "false") boolean actives,
                                                         CurrentUser user) {
        return ApiResponse.ok(sanctions.lister(actives, user));
    }

    @PostMapping("/sanctions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SanctionResponse> sanctionner(@Valid @RequestBody SanctionRequest req, CurrentUser user) {
        return ApiResponse.ok(sanctions.sanctionner(req, user), "Sanction enregistrée");
    }

    @DeleteMapping("/sanctions/{id}")
    public ApiResponse<SanctionResponse> lever(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(sanctions.lever(id, user), "Sanction levée");
    }

    @GetMapping("/utilisateurs/{id}/historique")
    public ApiResponse<HistoriqueMembreResponse> historique(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(sanctions.historique(id, user));
    }

    @GetMapping("/parametres")
    public ApiResponse<ParametresForumResponse> parametres(CurrentUser user) {
        return ApiResponse.ok(new ParametresForumResponse(reglages.globaux(user)));
    }

    @PutMapping("/parametres")
    public ApiResponse<ParametresForumResponse> modifierParametres(@Valid @RequestBody ParametresForumRequest req,
                                                                   CurrentUser user) {
        Map<String, String> valeurs = reglages.modifierGlobaux(req.valeurs(), user);
        return ApiResponse.ok(new ParametresForumResponse(valeurs), "Réglages enregistrés");
    }

    @GetMapping("/journal")
    public ApiResponse<PageResponse<JournalResponse>> journal(
            @RequestParam(required = false) Long acteur, @RequestParam(required = false) ActionModeration action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Integer page, CurrentUser user) {
        return ApiResponse.ok(reglages.journal(acteur, action, du, au, page, user));
    }

    /** Export CSV du journal (mêmes filtres), pour l'admin. */
    @GetMapping("/journal/export")
    public ResponseEntity<byte[]> journalCsv(
            @RequestParam(required = false) Long acteur, @RequestParam(required = false) ActionModeration action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            CurrentUser user) {
        byte[] csv = reglages.journalCsv(acteur, action, du, au, user).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"journal-moderation.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv);
    }
}

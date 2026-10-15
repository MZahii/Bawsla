package tn.esprit.bawsla.forum.controller;

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
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.AvisVigieRequest;
import tn.esprit.bawsla.forum.dto.BrouillonQuestionResponse;
import tn.esprit.bawsla.forum.dto.ConversationRequest;
import tn.esprit.bawsla.forum.dto.ConversationResponse;
import tn.esprit.bawsla.forum.dto.CorrectionVigieRequest;
import tn.esprit.bawsla.forum.dto.MessageVigieRequest;
import tn.esprit.bawsla.forum.dto.MessageVigieResponse;
import tn.esprit.bawsla.forum.dto.ModerationActionRequest;
import tn.esprit.bawsla.forum.dto.VigieFilResponse;
import tn.esprit.bawsla.forum.dto.VigieResponse;
import tn.esprit.bawsla.forum.service.AssistantService;
import tn.esprit.bawsla.forum.service.VigieService;

/** La Vigie : réponse dans les fils, avis, décisions du formateur, réponse générale, assistant privé. */
@RestController
@RequestMapping("/api/forum")
public class VigieController {

    private final VigieService vigie;
    private final AssistantService assistant;

    public VigieController(VigieService vigie, AssistantService assistant) {
        this.vigie = vigie;
        this.assistant = assistant;
    }

    // ---------------------------------------------------------------- Dans un fil

    @GetMapping("/discussions/{id}/vigie")
    public ApiResponse<VigieFilResponse> fil(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(vigie.fil(id, user));
    }

    @PostMapping("/discussions/{id}/vigie/regenerer")
    public ApiResponse<VigieFilResponse> regenerer(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(vigie.regenerer(id, user), "La Vigie prépare une nouvelle réponse");
    }

    /** Niveau 3 : réponse générale hors cours, sur demande de l'apprenant après une abstention. */
    @PostMapping("/discussions/{id}/vigie/general")
    public ApiResponse<VigieResponse> general(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(vigie.general(id, user));
    }

    @PostMapping("/vigie/{id}/avis")
    public ApiResponse<VigieResponse> avis(@PathVariable Long id, @Valid @RequestBody AvisVigieRequest req,
                                           CurrentUser user) {
        return ApiResponse.ok(vigie.avis(id, req.utile(), user), "Merci pour ton avis");
    }

    @PostMapping("/vigie/{id}/valider")
    public ApiResponse<VigieResponse> valider(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(vigie.valider(id, user), "Réponse de La Vigie validée");
    }

    @PostMapping("/vigie/{id}/corriger")
    public ApiResponse<VigieResponse> corriger(@PathVariable Long id, @Valid @RequestBody CorrectionVigieRequest req,
                                               CurrentUser user) {
        return ApiResponse.ok(vigie.corriger(id, req.texte(), user), "Réponse de La Vigie corrigée");
    }

    @PostMapping("/vigie/{id}/rejeter")
    public ApiResponse<VigieResponse> rejeter(@PathVariable Long id,
                                              @Valid @RequestBody(required = false) ModerationActionRequest req,
                                              CurrentUser user) {
        return ApiResponse.ok(vigie.rejeter(id, DiscussionController.motif(req), user), "Réponse de La Vigie rejetée");
    }

    // ---------------------------------------------------------------- Assistant privé « Demander au cours »

    @PostMapping("/vigie/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationResponse> creer(@Valid @RequestBody ConversationRequest req, CurrentUser user) {
        return ApiResponse.ok(assistant.creer(req.coursId(), user));
    }

    @GetMapping("/vigie/conversations")
    public ApiResponse<List<ConversationResponse>> conversations(CurrentUser user) {
        return ApiResponse.ok(assistant.lister(user));
    }

    @GetMapping("/vigie/conversations/{id}")
    public ApiResponse<ConversationResponse> conversation(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(assistant.lire(id, user));
    }

    @PostMapping("/vigie/conversations/{id}/messages")
    public ApiResponse<MessageVigieResponse> envoyer(@PathVariable Long id,
                                                     @Valid @RequestBody MessageVigieRequest req, CurrentUser user) {
        return ApiResponse.ok(assistant.envoyer(id, req, user));
    }

    @PostMapping("/vigie/conversations/{id}/publier")
    public ApiResponse<BrouillonQuestionResponse> publier(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(assistant.brouillon(id, user), "Brouillon prêt : relis-le avant de publier");
    }
}

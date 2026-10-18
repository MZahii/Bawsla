package tn.esprit.bawsla.cours.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CommandeResponse;
import tn.esprit.bawsla.cours.dto.PaiementRequest;
import tn.esprit.bawsla.cours.service.CommandeService;

/**
 * Achat d'un cours payant par paiement <b>simulé</b> (aucune somme débitée), achats de l'apprenant et ventes (formateur, ADMIN).
 * Les chemins fixes (/commandes/...) l'emportent sur /api/cours/{id}.
 */
@RestController
@RequestMapping("/api/cours")
public class CommandeController {

    private final CommandeService commandeService;

    public CommandeController(CommandeService commandeService) {
        this.commandeService = commandeService;
    }

    /** 201 à l'achat, 200 avec la commande existante si le cours était déjà acheté (idempotent). */
    @PostMapping("/{id}/commandes")
    public ResponseEntity<ApiResponse<CommandeResponse>> payer(@PathVariable Long id, @Valid @RequestBody PaiementRequest request,
                                                               CurrentUser user) {
        CommandeService.Payee resultat = commandeService.payer(id, request, user);
        return ResponseEntity.status(resultat.creee() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(resultat.commande(), resultat.creee() ? "Paiement simulé accepté" : "Cours déjà acheté"));
    }

    @GetMapping("/commandes/mes")
    public ApiResponse<List<CommandeResponse>> mesCommandes(CurrentUser user) {
        return ApiResponse.ok(commandeService.mesCommandes(user));
    }

    @GetMapping("/commandes/ventes")
    public ApiResponse<List<CommandeResponse>> ventes(CurrentUser user) {
        return ApiResponse.ok(commandeService.ventes(user));
    }
}

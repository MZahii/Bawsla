package tn.esprit.bawsla.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.AdminCreationRequest;
import tn.esprit.bawsla.user.dto.AdminModificationRequest;
import tn.esprit.bawsla.user.dto.ChangementMotDePasseRequest;
import tn.esprit.bawsla.user.dto.ApprenantsResponse;
import tn.esprit.bawsla.user.dto.DesactivationRequest;
import tn.esprit.bawsla.user.dto.IaApprenantResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse;
import tn.esprit.bawsla.user.dto.ParcoursDto;
import tn.esprit.bawsla.user.dto.MotDePasseRequest;
import tn.esprit.bawsla.user.dto.ProfilRequest;
import tn.esprit.bawsla.user.dto.StatutRequest;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.dto.UserPublicDto;
import tn.esprit.bawsla.user.service.ApprenantsService;
import tn.esprit.bawsla.user.service.IaApprenantService;
import tn.esprit.bawsla.user.service.IndicateursService;
import tn.esprit.bawsla.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final IndicateursService indicateursService;
    private final IaApprenantService iaApprenantService;
    private final ApprenantsService apprenantsService;

    public UserController(UserService userService, IndicateursService indicateursService,
                          IaApprenantService iaApprenantService, ApprenantsService apprenantsService) {
        this.userService = userService;
        this.indicateursService = indicateursService;
        this.iaApprenantService = iaApprenantService;
        this.apprenantsService = apprenantsService;
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(CurrentUser user) {
        return ApiResponse.ok(userService.me(user));
    }

    @PutMapping("/me")
    public ApiResponse<UserDto> modifierMonProfil(@Valid @RequestBody ProfilRequest request, CurrentUser user) {
        return ApiResponse.ok(userService.modifierMonProfil(user, request), "Profil enregistré");
    }

    @PutMapping("/me/mot-de-passe")
    public ApiResponse<Void> changerMonMotDePasse(@Valid @RequestBody ChangementMotDePasseRequest request,
                                                  CurrentUser user) {
        userService.changerMonMotDePasse(user, request);
        return ApiResponse.ok(null, "Mot de passe modifié");
    }

    /** « Supprimer mon compte » : désactivation, le mot de passe confirme. */
    @PostMapping("/me/desactivation")
    public ApiResponse<Void> desactiverMonCompte(@Valid @RequestBody DesactivationRequest request, CurrentUser user) {
        userService.desactiverMonCompte(user, request.motDePasse());
        return ApiResponse.ok(null, "Compte supprimé");
    }

    @GetMapping("/me/parcours")
    public ApiResponse<ParcoursDto> monParcours(CurrentUser user) {
        return ApiResponse.ok(userService.monParcours(user));
    }

    @PutMapping("/me/parcours")
    public ApiResponse<ParcoursDto> enregistrerMonParcours(@Valid @RequestBody ParcoursDto request, CurrentUser user) {
        return ApiResponse.ok(userService.enregistrerMonParcours(user, request), "Parcours enregistré");
    }

    /** Mes indicateurs pour le Machine Learning, avec l'état de chaque source (quiz-service, cours-service). */
    @GetMapping("/me/indicateurs")
    public ApiResponse<IndicateursResponse> mesIndicateurs(CurrentUser user) {
        return ApiResponse.ok(indicateursService.lesMiens(user));
    }

    /** Mon profil (K-Means) et mes cours recommandés ; jamais mon risque d'échec. */
    @GetMapping("/me/ia")
    public ApiResponse<IaApprenantResponse> monIa(CurrentUser user,
                                                  @RequestHeader(value = "Authorization", required = false) String jeton) {
        return ApiResponse.ok(iaApprenantService.pourMoi(user, jeton));
    }

    /** ENSEIGNANT ou ADMIN : risque d'échec (Random Forest) et profil d'un apprenant. */
    @GetMapping("/{id}/ia")
    public ApiResponse<IaApprenantResponse> iaDUnApprenant(@PathVariable Long id, CurrentUser user,
                                                           @RequestHeader(value = "Authorization", required = false) String jeton) {
        return ApiResponse.ok(iaApprenantService.pourUnApprenant(user, id, jeton));
    }

    /** ENSEIGNANT (ses cours) ou ADMIN (tous) : inscrits et risque d'échec, pour la page Apprenants. */
    @GetMapping("/formateur/apprenants")
    public ApiResponse<ApprenantsResponse> apprenantsDuFormateur(CurrentUser user,
                                                                 @RequestHeader(value = "Authorization", required = false) String jeton) {
        return ApiResponse.ok(apprenantsService.pourFormateur(user, jeton));
    }

    /** ENSEIGNANT ou ADMIN : indicateurs d'un apprenant. */
    @GetMapping("/{id}/indicateurs")
    public ApiResponse<IndicateursResponse> indicateurs(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(indicateursService.dUnApprenant(user, id));
    }

    /** ADMIN uniquement. */
    @GetMapping
    public ApiResponse<List<UserDto>> findAll(CurrentUser user) {
        return ApiResponse.ok(userService.findAll(user));
    }

    /**
     * Contrat inter-modules : infos publiques d'un utilisateur. Un visiteur (lecture publique du catalogue,
     * sans en-têtes d'identité) ne voit que les formateurs : jamais le nom d'un apprenant.
     */
    @GetMapping("/{id}")
    public ApiResponse<UserPublicDto> findById(@PathVariable Long id,
                                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(userId == null ? userService.findFormateurPublicById(id) : userService.findPublicById(id));
    }

    /** ADMIN uniquement. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto> creer(@Valid @RequestBody AdminCreationRequest request, CurrentUser user) {
        return ApiResponse.ok(userService.creer(user, request), "Compte créé");
    }

    /** ADMIN uniquement. */
    @PutMapping("/{id}")
    public ApiResponse<UserDto> modifier(@PathVariable Long id, @Valid @RequestBody AdminModificationRequest request,
                                         CurrentUser user) {
        return ApiResponse.ok(userService.modifier(user, id, request), "Compte modifié");
    }

    /** ADMIN uniquement : activer ou désactiver un compte. */
    @PatchMapping("/{id}/statut")
    public ApiResponse<UserDto> changerStatut(@PathVariable Long id, @Valid @RequestBody StatutRequest request,
                                              CurrentUser user) {
        UserDto dto = userService.changerStatut(user, id, request.actif());
        return ApiResponse.ok(dto, dto.actif() ? "Compte activé" : "Compte désactivé");
    }

    /** ADMIN uniquement : nouveau mot de passe (lève aussi le verrouillage). */
    @PutMapping("/{id}/mot-de-passe")
    public ApiResponse<Void> reinitialiserMotDePasse(@PathVariable Long id,
                                                     @Valid @RequestBody MotDePasseRequest request,
                                                     CurrentUser user) {
        userService.reinitialiserMotDePasse(user, id, request.motDePasse());
        return ApiResponse.ok(null, "Mot de passe réinitialisé");
    }
}

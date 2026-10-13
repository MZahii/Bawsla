package tn.esprit.bawsla.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.AdminCreationRequest;
import tn.esprit.bawsla.user.dto.AdminModificationRequest;
import tn.esprit.bawsla.user.dto.ChangementMotDePasseRequest;
import tn.esprit.bawsla.user.dto.MotDePasseRequest;
import tn.esprit.bawsla.user.dto.ProfilRequest;
import tn.esprit.bawsla.user.dto.StatutRequest;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.dto.UserPublicDto;
import tn.esprit.bawsla.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

    /** ADMIN uniquement. */
    @GetMapping
    public ApiResponse<List<UserDto>> findAll(CurrentUser user) {
        return ApiResponse.ok(userService.findAll(user));
    }

    /** Contrat inter-modules : infos publiques d'un utilisateur. */
    @GetMapping("/{id}")
    public ApiResponse<UserPublicDto> findById(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(userService.findPublicById(id));
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

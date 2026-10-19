package tn.esprit.bawsla.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.dto.AuthResponse;
import tn.esprit.bawsla.user.dto.LoginRequest;
import tn.esprit.bawsla.user.dto.MotDePasseOublieRequest;
import tn.esprit.bawsla.user.dto.RegisterRequest;
import tn.esprit.bawsla.user.dto.ReinitialisationRequest;
import tn.esprit.bawsla.user.service.AuthService;
import tn.esprit.bawsla.user.service.ReinitialisationService;

/** Endpoints PUBLICS (laissés passer sans JWT par l'api-gateway). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ReinitialisationService reinitialisationService;

    public AuthController(AuthService authService, ReinitialisationService reinitialisationService) {
        this.authService = authService;
        this.reinitialisationService = reinitialisationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request), "Inscription réussie");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request), "Connexion réussie");
    }

    /** Toujours 200, que le compte existe ou non. */
    @PostMapping("/mot-de-passe-oublie")
    public ApiResponse<Void> motDePasseOublie(@Valid @RequestBody MotDePasseOublieRequest request) {
        reinitialisationService.demander(request);
        return ApiResponse.ok(null, "Si un compte existe pour cet email, un lien de réinitialisation vient d'être envoyé");
    }

    @PostMapping("/reinitialiser-mot-de-passe")
    public ApiResponse<Void> reinitialiser(@Valid @RequestBody ReinitialisationRequest request) {
        reinitialisationService.reinitialiser(request);
        return ApiResponse.ok(null, "Mot de passe modifié. Tu peux te connecter");
    }
}

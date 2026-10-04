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
import tn.esprit.bawsla.user.dto.RegisterRequest;
import tn.esprit.bawsla.user.service.AuthService;

/** Endpoints PUBLICS (laissés passer sans JWT par l'api-gateway). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}

package tn.esprit.bawsla.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ConflictException;
import tn.esprit.bawsla.user.common.exception.UnauthorizedException;
import tn.esprit.bawsla.user.dto.AuthResponse;
import tn.esprit.bawsla.user.dto.LoginRequest;
import tn.esprit.bawsla.user.dto.RegisterRequest;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Role role = request.role() == null ? Role.ETUDIANT : request.role();
        if (role == Role.ADMIN) {
            throw new BadRequestException("Impossible de s'inscrire avec le rôle ADMIN");
        }
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Un compte existe déjà avec cet email");
        }
        User user = new User();
        user.setNom(request.nom().trim());
        user.setPrenom(request.prenom().trim());
        user.setEmail(email);
        user.setMotDePasse(passwordEncoder.encode(request.motDePasse()));
        user.setRole(role);
        userRepository.save(user);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.motDePasse(), u.getMotDePasse()))
                .orElseThrow(() -> new UnauthorizedException("Email ou mot de passe incorrect"));
        if (!user.isActif()) {
            throw new UnauthorizedException("Compte désactivé");
        }
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.bearer(jwtService.generate(user), jwtService.getExpirationMs(), UserDto.from(user));
    }
}

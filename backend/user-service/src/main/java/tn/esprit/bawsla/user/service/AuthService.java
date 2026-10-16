package tn.esprit.bawsla.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

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

    /** Après 5 échecs consécutifs, le compte est verrouillé 15 minutes. */
    static final int MAX_TENTATIVES = 5;
    static final Duration DUREE_VERROU = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DemandeFormateurService demandeFormateurService;
    private final ActiviteService activiteService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       DemandeFormateurService demandeFormateurService, ActiviteService activiteService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.demandeFormateurService = demandeFormateurService;
        this.activiteService = activiteService;
        this.clock = clock;
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
        // Un formateur s'inscrit non validé : sa demande part à l'admin s'il a décrit son expertise
        // (sinon il pourra la faire plus tard depuis « Mon compte »).
        if (role == Role.ENSEIGNANT && request.expertise() != null && !request.expertise().isBlank()) {
            demandeFormateurService.creerPourInscription(user, request.expertise(), request.presentation(), request.lien());
        }
        return toAuthResponse(user);
    }

    /**
     * noRollbackFor : un échec doit rester compté en base même si l'on répond 401.
     * Le verrou est vérifié avant le mot de passe, pour qu'un attaquant ne puisse plus tester de mot de passe.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(AuthService::identifiantsInvalides);
        LocalDateTime maintenant = LocalDateTime.now(clock);
        if (user.estVerrouille(maintenant)) {
            long minutes = Math.ceilDiv(Duration.between(maintenant, user.getVerrouilleJusqua()).toSeconds(), 60);
            throw new UnauthorizedException(
                    "Trop de tentatives échouées. Réessaie dans " + minutes + " minute" + (minutes > 1 ? "s" : ""));
        }
        if (!passwordEncoder.matches(request.motDePasse(), user.getMotDePasse())) {
            user.enregistrerEchec(maintenant, MAX_TENTATIVES, DUREE_VERROU);
            throw identifiantsInvalides();
        }
        if (!user.isActif()) {
            throw new UnauthorizedException("Compte désactivé");
        }
        user.enregistrerConnexion(maintenant);
        activiteService.enregistrer(user.getId());
        return toAuthResponse(user);
    }

    private static UnauthorizedException identifiantsInvalides() {
        return new UnauthorizedException("Email ou mot de passe incorrect");
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.bearer(jwtService.generate(user), jwtService.getExpirationMs(), UserDto.from(user));
    }
}

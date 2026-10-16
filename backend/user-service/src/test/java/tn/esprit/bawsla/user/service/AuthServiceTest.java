package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ConflictException;
import tn.esprit.bawsla.user.common.exception.UnauthorizedException;
import tn.esprit.bawsla.user.dto.AuthResponse;
import tn.esprit.bawsla.user.dto.LoginRequest;
import tn.esprit.bawsla.user.dto.RegisterRequest;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    private static final Instant T0 = Instant.parse("2026-10-13T10:00:00Z");
    private static final String MDP = "Password1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private DemandeFormateurService demandeFormateurService;

    @Mock
    private ActiviteService activiteService;

    /** Coût BCrypt minimal : les tests restent rapides. */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = new JwtService("un-secret-de-test-d-au-moins-32-caracteres!!", 3_600_000);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = serviceA(T0);
    }

    private AuthService serviceA(Instant instant) {
        return new AuthService(userRepository, encoder, jwtService, demandeFormateurService, activiteService, Clock.fixed(instant, ZONE));
    }

    private User compte(boolean actif) {
        User u = new User();
        u.setId(7L);
        u.setNom("Ben Ali");
        u.setPrenom("Sami");
        u.setEmail("sami@example.com");
        u.setMotDePasse(encoder.encode(MDP));
        u.setRole(Role.ETUDIANT);
        u.setActif(actif);
        return u;
    }

    // ---------- Inscription ----------

    @Test
    void register_creeUnApprenantParDefaut_avecEmailNormaliseEtMotDePasseHache() {
        when(userRepository.existsByEmailIgnoreCase("sami@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthResponse res = service.register(new RegisterRequest(" Ben Ali ", "Sami", " Sami@Example.COM ", MDP, null));

        assertThat(res.token()).isNotBlank();
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.user().email()).isEqualTo("sami@example.com");
        assertThat(res.user().nom()).isEqualTo("Ben Ali");
        assertThat(res.user().role()).isEqualTo(Role.ETUDIANT);
    }

    @Test
    void register_neStockeJamaisLeMotDePasseEnClair() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            assertThat(u.getMotDePasse()).isNotEqualTo(MDP).startsWith("$2");
            assertThat(encoder.matches(MDP, u.getMotDePasse())).isTrue();
            return u;
        });

        service.register(new RegisterRequest("A", "B", "a@b.com", MDP, Role.ENSEIGNANT));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_formateurAvecExpertise_creeUneDemandeEnAttente_etNonValide() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse res = service.register(new RegisterRequest("A", "B", "f@b.com", MDP, Role.ENSEIGNANT,
                "Bases de données", "10 ans de SQL", "linkedin.com/in/x"));

        assertThat(res.user().role()).isEqualTo(Role.ENSEIGNANT);
        assertThat(res.user().formateurValide()).isFalse();
        verify(demandeFormateurService).creerPourInscription(any(User.class),
                org.mockito.ArgumentMatchers.eq("Bases de données"), org.mockito.ArgumentMatchers.eq("10 ans de SQL"),
                org.mockito.ArgumentMatchers.eq("linkedin.com/in/x"));
    }

    @Test
    void register_apprenantOuFormateurSansExpertise_pasDeDemande() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(new RegisterRequest("A", "B", "e@b.com", MDP, Role.ETUDIANT, "ignoré", null, null));
        service.register(new RegisterRequest("A", "B", "f@b.com", MDP, Role.ENSEIGNANT, "  ", null, null));

        verify(demandeFormateurService, never()).creerPourInscription(any(), any(), any(), any());
    }

    @Test
    void register_refuseLeRoleAdmin() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("A", "B", "a@b.com", MDP, Role.ADMIN)))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_refuseUnEmailDejaUtilise() {
        when(userRepository.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("A", "B", "A@B.com", MDP, null)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    // ---------- Connexion ----------

    @Test
    void login_reussi_renvoieUnTokenEtEnregistreLaConnexion() {
        User u = compte(true);
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(u));

        AuthResponse res = service.login(new LoginRequest(" sami@example.com ", MDP));

        assertThat(res.token()).isNotBlank();
        assertThat(res.user().id()).isEqualTo(7L);
        assertThat(u.getDerniereConnexion()).isEqualTo(LocalDateTime.ofInstant(T0, ZONE));
        assertThat(res.user().derniereConnexion()).isEqualTo(u.getDerniereConnexion());
        verify(activiteService).enregistrer(7L);
    }

    @Test
    void login_emailInconnu_memeMessageQueMauvaisMotDePasse() {
        when(userRepository.findByEmailIgnoreCase("x@y.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("x@y.com", MDP)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Email ou mot de passe incorrect");
    }

    @Test
    void login_mauvaisMotDePasse_compteLEchec() {
        User u = compte(true);
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("sami@example.com", "mauvais1")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Email ou mot de passe incorrect");
        assertThat(u.getTentativesEchouees()).isEqualTo(1);
        assertThat(u.getDerniereConnexion()).isNull();
    }

    @Test
    void login_compteDesactive_refuse() {
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(compte(false)));

        assertThatThrownBy(() -> service.login(new LoginRequest("sami@example.com", MDP)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Compte désactivé");
    }

    @Test
    void login_verrouilleApres5Echecs_memeAvecLeBonMotDePasse() {
        User u = compte(true);
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(u));

        for (int i = 0; i < AuthService.MAX_TENTATIVES; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("sami@example.com", "mauvais1")))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThat(u.getVerrouilleJusqua()).isEqualTo(LocalDateTime.ofInstant(T0, ZONE).plus(AuthService.DUREE_VERROU));
        assertThatThrownBy(() -> service.login(new LoginRequest("sami@example.com", MDP)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Trop de tentatives échouées. Réessaie dans 15 minutes");
    }

    @Test
    void login_deverrouilleApresLeDelai_etRemetLeCompteurAZero() {
        User u = compte(true);
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(u));
        for (int i = 0; i < AuthService.MAX_TENTATIVES; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("sami@example.com", "mauvais1")))
                    .isInstanceOf(UnauthorizedException.class);
        }

        AuthService plusTard = serviceA(T0.plus(AuthService.DUREE_VERROU).plusSeconds(1));
        assertThat(plusTard.login(new LoginRequest("sami@example.com", MDP)).token()).isNotBlank();
        assertThat(u.getVerrouilleJusqua()).isNull();
        assertThat(u.getTentativesEchouees()).isZero();
    }

    @Test
    void login_reussi_remetLeCompteurDEchecsAZero() {
        User u = compte(true);
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login(new LoginRequest("sami@example.com", "mauvais1")))
                .isInstanceOf(UnauthorizedException.class);

        service.login(new LoginRequest("sami@example.com", MDP));

        assertThat(u.getTentativesEchouees()).isZero();
    }
}

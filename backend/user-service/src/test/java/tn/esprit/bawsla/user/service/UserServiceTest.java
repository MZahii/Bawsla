package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ConflictException;
import tn.esprit.bawsla.user.common.exception.ForbiddenException;
import tn.esprit.bawsla.user.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.AdminCreationRequest;
import tn.esprit.bawsla.user.dto.AdminModificationRequest;
import tn.esprit.bawsla.user.dto.ChangementMotDePasseRequest;
import tn.esprit.bawsla.user.dto.ProfilRequest;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser(1L, CurrentUser.ADMIN);
    private static final CurrentUser APPRENANT = new CurrentUser(5L, CurrentUser.ETUDIANT);

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private UserService service() {
        return new UserService(userRepository, encoder);
    }

    private static User compte(long id, Role role, boolean actif) {
        User u = new User();
        u.setId(id);
        u.setNom("Nom" + id);
        u.setPrenom("Prenom" + id);
        u.setEmail("u" + id + "@example.com");
        u.setMotDePasse("$2a$04$hash");
        u.setRole(role);
        u.setActif(actif);
        return u;
    }

    private void existe(User u) {
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
    }

    // ---------- Autorisation ----------

    @Test
    void lesActionsAdminSontInterditesAuxAutresRoles() {
        UserService s = service();
        assertThatThrownBy(() -> s.findAll(APPRENANT)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> s.creer(APPRENANT, new AdminCreationRequest("A", "B", "a@b.com", "Password1", Role.ADMIN, true)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> s.changerStatut(APPRENANT, 2L, false)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> s.reinitialiserMotDePasse(APPRENANT, 2L, "Password1")).isInstanceOf(ForbiddenException.class);
        verify(userRepository, never()).save(any());
    }

    // ---------- Création ----------

    @Test
    void creer_hacheLeMotDePasse_normaliseLEmail_etActifParDefaut() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto dto = service().creer(ADMIN,
                new AdminCreationRequest(" Ayari ", "Imen", " Imen@Example.COM ", "Password1", Role.ENSEIGNANT, null));

        assertThat(dto.email()).isEqualTo("imen@example.com");
        assertThat(dto.nom()).isEqualTo("Ayari");
        assertThat(dto.role()).isEqualTo(Role.ENSEIGNANT);
        assertThat(dto.actif()).isTrue();
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(u -> encoder.matches("Password1", u.getMotDePasse())));
    }

    @Test
    void creer_refuseUnEmailDejaPris() {
        when(userRepository.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service().creer(ADMIN, new AdminCreationRequest("A", "B", "A@B.com", "Password1", Role.ETUDIANT, true)))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- Modification ----------

    @Test
    void modifier_changeLesChamps() {
        User u = compte(2, Role.ETUDIANT, true);
        existe(u);

        UserDto dto = service().modifier(ADMIN, 2L, new AdminModificationRequest("Gafsi", "Rim", "rim@x.com", Role.ENSEIGNANT, false));

        assertThat(dto.nom()).isEqualTo("Gafsi");
        assertThat(dto.email()).isEqualTo("rim@x.com");
        assertThat(dto.role()).isEqualTo(Role.ENSEIGNANT);
        assertThat(dto.actif()).isFalse();
    }

    @Test
    void modifier_refuseLEmailDUnAutreCompte() {
        existe(compte(2, Role.ETUDIANT, true));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("pris@x.com", 2L)).thenReturn(true);

        assertThatThrownBy(() -> service().modifier(ADMIN, 2L, new AdminModificationRequest("A", "B", "pris@x.com", Role.ETUDIANT, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void modifier_compteInexistant_404() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().modifier(ADMIN, 99L, new AdminModificationRequest("A", "B", "a@b.com", Role.ETUDIANT, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- Garde-fous administrateur ----------

    @Test
    void lAdminNePeutPasSeDesactiverLuiMeme() {
        existe(compte(1, Role.ADMIN, true));

        assertThatThrownBy(() -> service().changerStatut(ADMIN, 1L, false)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void lAdminNePeutPasSeRetirerLeRoleAdmin() {
        existe(compte(1, Role.ADMIN, true));

        assertThatThrownBy(() -> service().modifier(ADMIN, 1L, new AdminModificationRequest("A", "B", "u1@example.com", Role.ETUDIANT, true)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void leDernierAdminActifNePeutPasEtreDesactive() {
        existe(compte(3, Role.ADMIN, true));
        when(userRepository.countByRoleAndActifTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service().changerStatut(ADMIN, 3L, false)).isInstanceOf(ConflictException.class);
    }

    @Test
    void unAutreAdminPeutEtreDesactiveSIlEnResteUn() {
        existe(compte(3, Role.ADMIN, true));
        when(userRepository.countByRoleAndActifTrue(Role.ADMIN)).thenReturn(2L);

        assertThat(service().changerStatut(ADMIN, 3L, false).actif()).isFalse();
    }

    @Test
    void desactiverUnApprenant_sansControleAdmin() {
        existe(compte(2, Role.ETUDIANT, true));

        assertThat(service().changerStatut(ADMIN, 2L, false).actif()).isFalse();
        verify(userRepository, never()).countByRoleAndActifTrue(any());
    }

    // ---------- Mon profil ----------

    @Test
    void modifierMonProfil_neTouchePasALEmailNiAuRole() {
        User u = compte(5, Role.ETUDIANT, true);
        existe(u);

        UserDto dto = service().modifierMonProfil(APPRENANT, new ProfilRequest(" Trabelsi ", " Lina ", "  J'apprends Spring.  "));

        assertThat(dto.nom()).isEqualTo("Trabelsi");
        assertThat(dto.prenom()).isEqualTo("Lina");
        assertThat(dto.bio()).isEqualTo("J'apprends Spring.");
        assertThat(dto.email()).isEqualTo("u5@example.com");
        assertThat(dto.role()).isEqualTo(Role.ETUDIANT);
    }

    @Test
    void modifierMonProfil_bioVideDevientNull() {
        User u = compte(5, Role.ETUDIANT, true);
        u.setBio("ancienne");
        existe(u);

        assertThat(service().modifierMonProfil(APPRENANT, new ProfilRequest("A", "B", "   ")).bio()).isNull();
    }

    @Test
    void changerMonMotDePasse_exigeLAncien() {
        User u = compte(5, Role.ETUDIANT, true);
        u.setMotDePasse(encoder.encode("Password1"));
        existe(u);

        assertThatThrownBy(() -> service().changerMonMotDePasse(APPRENANT, new ChangementMotDePasseRequest("faux", "Nouveau123")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mot de passe actuel incorrect");
        assertThat(encoder.matches("Password1", u.getMotDePasse())).isTrue();
    }

    @Test
    void changerMonMotDePasse_refuseLeMemeMotDePasse() {
        User u = compte(5, Role.ETUDIANT, true);
        u.setMotDePasse(encoder.encode("Password1"));
        existe(u);

        assertThatThrownBy(() -> service().changerMonMotDePasse(APPRENANT, new ChangementMotDePasseRequest("Password1", "Password1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changerMonMotDePasse_ok() {
        User u = compte(5, Role.ETUDIANT, true);
        u.setMotDePasse(encoder.encode("Password1"));
        existe(u);

        service().changerMonMotDePasse(APPRENANT, new ChangementMotDePasseRequest("Password1", "Nouveau123"));

        assertThat(encoder.matches("Nouveau123", u.getMotDePasse())).isTrue();
    }

    // ---------- Mot de passe ----------

    @Test
    void reinitialiserMotDePasse_hacheEtDeverrouille() {
        User u = compte(2, Role.ETUDIANT, true);
        LocalDateTime t = LocalDateTime.of(2026, 10, 13, 10, 0);
        for (int i = 0; i < 5; i++) {
            u.enregistrerEchec(t, 5, Duration.ofMinutes(15));
        }
        assertThat(u.estVerrouille(t)).isTrue();
        existe(u);

        service().reinitialiserMotDePasse(ADMIN, 2L, "Nouveau123");

        assertThat(encoder.matches("Nouveau123", u.getMotDePasse())).isTrue();
        assertThat(u.estVerrouille(t)).isFalse();
        assertThat(u.getTentativesEchouees()).isZero();
    }
}

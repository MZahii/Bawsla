package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.dto.MotDePasseOublieRequest;
import tn.esprit.bawsla.user.dto.ReinitialisationRequest;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReinitialisationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    private static final Instant T0 = Instant.parse("2026-10-19T10:00:00Z");
    private static final LocalDateTime MAINTENANT = LocalDateTime.ofInstant(T0, ZONE);

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private ReinitialisationService serviceA(Instant instant) {
        return new ReinitialisationService(userRepository, encoder, mailService, Clock.fixed(instant, ZONE), "http://front.test/");
    }

    private User compte(boolean actif) {
        User u = new User();
        u.setId(7L);
        u.setNom("Ben Ali");
        u.setPrenom("Sami");
        u.setEmail("sami@example.com");
        u.setMotDePasse(encoder.encode("Ancien123"));
        u.setRole(Role.ETUDIANT);
        u.setActif(actif);
        return u;
    }

    /** Lance une demande et renvoie le jeton en clair contenu dans le lien envoyé. */
    private String demanderEtRecupererJeton(User u) {
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(u));
        serviceA(T0).demander(new MotDePasseOublieRequest("  sami@example.com "));
        ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
        verify(mailService).envoyerLienReinitialisation(eq("sami@example.com"), eq("Sami"), lien.capture(), eq(30L));
        assertThat(lien.getValue()).startsWith("http://front.test/reinitialiser?token=");
        return lien.getValue().substring(lien.getValue().indexOf('=') + 1);
    }

    @Test
    void demander_emailInconnu_neFaitRienEtNeLevePasDErreur() {
        when(userRepository.findByEmailIgnoreCase("inconnu@example.com")).thenReturn(Optional.empty());

        serviceA(T0).demander(new MotDePasseOublieRequest("inconnu@example.com"));

        verify(mailService, never()).envoyerLienReinitialisation(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void demander_compteDesactive_nEnvoiePasDeLien() {
        User u = compte(false);
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(u));

        serviceA(T0).demander(new MotDePasseOublieRequest("sami@example.com"));

        verify(mailService, never()).envoyerLienReinitialisation(anyString(), anyString(), anyString(), anyLong());
        assertThat(u.getReinitialisationHash()).isNull();
    }

    @Test
    void demander_stockeSeulementLEmpreinteDuJeton_valable30Minutes() {
        User u = compte(true);

        String jeton = demanderEtRecupererJeton(u);

        assertThat(jeton).hasSizeGreaterThanOrEqualTo(43);
        assertThat(u.getReinitialisationHash()).isNotEqualTo(jeton).isEqualTo(ReinitialisationService.empreinte(jeton));
        assertThat(u.getReinitialisationExpire()).isEqualTo(MAINTENANT.plusMinutes(30));
    }

    @Test
    void demander_moinsDUneMinuteApres_neRenvoiePasDeLien() {
        User u = compte(true);
        u.preparerReinitialisation("ancienne-empreinte", MAINTENANT.plusMinutes(30).minusSeconds(20));
        when(userRepository.findByEmailIgnoreCase("sami@example.com")).thenReturn(Optional.of(u));

        serviceA(T0).demander(new MotDePasseOublieRequest("sami@example.com"));

        verify(mailService, never()).envoyerLienReinitialisation(anyString(), anyString(), anyString(), anyLong());
        assertThat(u.getReinitialisationHash()).isEqualTo("ancienne-empreinte");
    }

    @Test
    void reinitialiser_jetonValide_changeLeMotDePasse_deverrouilleEtInvalideLeLien() {
        User u = compte(true);
        u.enregistrerEchec(MAINTENANT, 1, Duration.ofMinutes(15));
        assertThat(u.estVerrouille(MAINTENANT)).isTrue();
        String jeton = demanderEtRecupererJeton(u);
        when(userRepository.findByReinitialisationHash(ReinitialisationService.empreinte(jeton))).thenReturn(Optional.of(u));

        serviceA(T0.plusSeconds(600)).reinitialiser(new ReinitialisationRequest(jeton, "Nouveau123"));

        assertThat(encoder.matches("Nouveau123", u.getMotDePasse())).isTrue();
        assertThat(u.estVerrouille(MAINTENANT)).isFalse();
        assertThat(u.getReinitialisationHash()).isNull();
        assertThat(u.getReinitialisationExpire()).isNull();
    }

    @Test
    void reinitialiser_jetonExpire_estRefuse() {
        User u = compte(true);
        String jeton = demanderEtRecupererJeton(u);
        String ancienHash = u.getMotDePasse();
        when(userRepository.findByReinitialisationHash(ReinitialisationService.empreinte(jeton))).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> serviceA(T0.plus(Duration.ofMinutes(31))).reinitialiser(new ReinitialisationRequest(jeton, "Nouveau123")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ReinitialisationService.LIEN_INVALIDE);
        assertThat(u.getMotDePasse()).isEqualTo(ancienHash);
    }

    @Test
    void reinitialiser_jetonInconnuOuDejaUtilise_estRefuse() {
        when(userRepository.findByReinitialisationHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceA(T0).reinitialiser(new ReinitialisationRequest("jeton-bidon", "Nouveau123")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ReinitialisationService.LIEN_INVALIDE);
    }
}

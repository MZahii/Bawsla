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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ConflictException;
import tn.esprit.bawsla.user.common.exception.ForbiddenException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.DemandeFormateurDto;
import tn.esprit.bawsla.user.dto.DemandeFormateurRequest;
import tn.esprit.bawsla.user.entity.DemandeFormateur;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.StatutDemande;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.DemandeFormateurRepository;
import tn.esprit.bawsla.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DemandeFormateurServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    private static final Instant T0 = Instant.parse("2026-10-13T10:00:00Z");
    private static final CurrentUser ADMIN = new CurrentUser(1L, CurrentUser.ADMIN);
    private static final CurrentUser APPRENANT = new CurrentUser(5L, CurrentUser.ETUDIANT);

    @Mock
    private DemandeFormateurRepository demandeRepository;

    @Mock
    private UserRepository userRepository;

    private DemandeFormateurService service() {
        return new DemandeFormateurService(demandeRepository, userRepository, Clock.fixed(T0, ZONE));
    }

    private static User compte(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setNom("Nom");
        u.setPrenom("Prenom");
        u.setEmail("u" + id + "@example.com");
        u.setRole(role);
        return u;
    }

    private static DemandeFormateur demande(long id, User u) {
        DemandeFormateur d = new DemandeFormateur();
        d.setId(id);
        d.setUtilisateur(u);
        d.setExpertise("Data");
        d.setDateCreation(LocalDateTime.of(2026, 10, 12, 9, 0));
        return d;
    }

    // ---------- Soumission ----------

    @Test
    void soumettre_creeUneDemandeEnAttente_avecChampsNettoyes() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(compte(5, Role.ETUDIANT)));
        when(demandeRepository.save(any(DemandeFormateur.class))).thenAnswer(inv -> inv.getArgument(0));

        DemandeFormateurDto dto = service().soumettre(APPRENANT,
                new DemandeFormateurRequest("  Cybersécurité ", "   ", " github.com/x "));

        assertThat(dto.statut()).isEqualTo(StatutDemande.EN_ATTENTE);
        assertThat(dto.expertise()).isEqualTo("Cybersécurité");
        assertThat(dto.presentation()).isNull();
        assertThat(dto.lien()).isEqualTo("github.com/x");
        assertThat(dto.dateCreation()).isEqualTo(LocalDateTime.ofInstant(T0, ZONE));
    }

    @Test
    void soumettre_refuseSiUneDemandeEstDejaEnCours() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(compte(5, Role.ETUDIANT)));
        when(demandeRepository.existsByUtilisateurIdAndStatut(5L, StatutDemande.EN_ATTENTE)).thenReturn(true);

        assertThatThrownBy(() -> service().soumettre(APPRENANT, new DemandeFormateurRequest("Data", null, null)))
                .isInstanceOf(ConflictException.class);
        verify(demandeRepository, never()).save(any());
    }

    @Test
    void soumettre_refuseUnFormateurDejaValide() {
        User u = compte(5, Role.ENSEIGNANT);
        u.setFormateurValide(true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service().soumettre(APPRENANT, new DemandeFormateurRequest("Data", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void soumettre_refuseUnAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(compte(1, Role.ADMIN)));

        assertThatThrownBy(() -> service().soumettre(ADMIN, new DemandeFormateurRequest("Data", null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    // ---------- Traitement par l'admin ----------

    @Test
    void lesActionsAdminSontInterditesAuxAutres() {
        assertThatThrownBy(() -> service().lister(APPRENANT)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service().accepter(APPRENANT, 3L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service().refuser(APPRENANT, 3L, "non")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void accepter_unApprenantDevientFormateurValide() {
        User u = compte(5, Role.ETUDIANT);
        when(demandeRepository.findById(3L)).thenReturn(Optional.of(demande(3, u)));

        DemandeFormateurDto dto = service().accepter(ADMIN, 3L);

        assertThat(dto.statut()).isEqualTo(StatutDemande.ACCEPTEE);
        assertThat(dto.dateTraitement()).isEqualTo(LocalDateTime.ofInstant(T0, ZONE));
        assertThat(u.getRole()).isEqualTo(Role.ENSEIGNANT);
        assertThat(u.isFormateurValide()).isTrue();
    }

    @Test
    void refuser_garderLeRole_etEnregistreLeMotif() {
        User u = compte(5, Role.ENSEIGNANT);
        DemandeFormateur d = demande(3, u);
        when(demandeRepository.findById(3L)).thenReturn(Optional.of(d));

        DemandeFormateurDto dto = service().refuser(ADMIN, 3L, "  Précise ton expérience.  ");

        assertThat(dto.statut()).isEqualTo(StatutDemande.REFUSEE);
        assertThat(dto.motifRefus()).isEqualTo("Précise ton expérience.");
        assertThat(d.getTraiteePar()).isEqualTo(1L);
        assertThat(u.getRole()).isEqualTo(Role.ENSEIGNANT);
        assertThat(u.isFormateurValide()).isFalse();
    }

    @Test
    void uneDemandeDejaTraiteeNePeutPasLEtreDeNouveau() {
        DemandeFormateur d = demande(3, compte(5, Role.ETUDIANT));
        d.refuser(1L, "non", LocalDateTime.of(2026, 10, 12, 10, 0));
        when(demandeRepository.findById(3L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service().accepter(ADMIN, 3L)).isInstanceOf(ConflictException.class);
    }
}

package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import feign.FeignException;
import tn.esprit.bawsla.user.client.AiClient;
import tn.esprit.bawsla.user.client.CoursClient;
import tn.esprit.bawsla.user.client.CoursDto;
import tn.esprit.bawsla.user.client.InscriptionDto;
import tn.esprit.bawsla.user.client.ProfilIaDto;
import tn.esprit.bawsla.user.client.RisqueIaDto;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.exception.ForbiddenException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.ApprenantsResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Indicateurs;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Statut;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ApprenantsServiceTest {

    private static final String JETON = "Bearer abc";
    private static final CurrentUser FORMATEUR = new CurrentUser(2L, CurrentUser.ENSEIGNANT);

    @Mock private CoursClient coursClient;
    @Mock private IndicateursService indicateursService;
    @Mock private AiClient aiClient;
    @Mock private UserRepository userRepository;
    @InjectMocks private ApprenantsService service;

    private static final List<CoursDto> CATALOGUE = List.of(
            new CoursDto(10L, "SQL", "Données", "DEBUTANT", "PUBLIE", 2L),
            new CoursDto(11L, "Spring", "Back-end", "INTERMEDIAIRE", "BROUILLON", 2L),
            new CoursDto(12L, "React", "Web", "DEBUTANT", "PUBLIE", 3L));

    private static User apprenant(long id, String prenom) {
        User u = new User();
        u.setId(id);
        u.setNom("Nom");
        u.setPrenom(prenom);
        u.setRole(Role.ETUDIANT);
        return u;
    }

    private static InscriptionDto inscription(long etudiant, long cours, double progression) {
        return new InscriptionDto(etudiant, cours, null, progression, 100, false, null);
    }

    private static IndicateursResponse indicateurs(boolean complet) {
        Indicateurs i = complet ? new Indicateurs(0.4, 0.4, 3, 6, 12, 0.3, 120, 1, 0, 3, 20)
                : new Indicateurs(null, null, null, null, null, null, null, null, null, 2, 1);
        return new IndicateursResponse(i, List.of(), List.of(), List.of(), Map.of(), complet);
    }

    private static FeignException statut(int code) {
        return new FeignException(code, "réponse " + code) { };
    }

    @Test
    void reserveAuxFormateursEtAdmins() {
        assertThatThrownBy(() -> service.pourFormateur(new CurrentUser(5L, CurrentUser.ETUDIANT), JETON))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void seulementSesCours_risqueEnUnSeulAppel_pourLesDonneesCompletes() {
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(CATALOGUE));
        when(coursClient.inscriptionsDuCours(10L)).thenReturn(ApiResponse.ok(List.of(inscription(5, 10, 0.3), inscription(6, 10, 0.9))));
        when(coursClient.inscriptionsDuCours(11L)).thenReturn(ApiResponse.ok(List.of(inscription(5, 11, 0.1))));
        when(userRepository.findById(5L)).thenReturn(Optional.of(apprenant(5, "Lina")));
        when(userRepository.findById(6L)).thenReturn(Optional.of(apprenant(6, "Sami")));
        when(indicateursService.calculer(5L)).thenReturn(indicateurs(true));
        when(indicateursService.calculer(6L)).thenReturn(indicateurs(false));
        when(aiClient.risque(eq(JETON), any())).thenReturn(ApiResponse.ok(new RisqueIaDto(
                List.of(new RisqueIaDto.Resultat(0.9, "eleve", List.of("Note moyenne basse aux quiz (40%)"))), 0.4, true)));
        when(aiClient.profil(eq(JETON), any())).thenReturn(ApiResponse.ok(
                new ProfilIaDto(List.of(new ProfilIaDto.Groupe(0, "Décrocheur", "…")), true)));

        ApprenantsResponse r = service.pourFormateur(FORMATEUR, JETON);

        assertThat(r.cours()).extracting(ApprenantsResponse.CoursResume::id).containsExactly(10L, 11L);
        verify(coursClient, never()).inscriptionsDuCours(12L); // pas son cours
        assertThat(r.lignes()).hasSize(3);
        var lina = r.lignes().stream().filter(l -> l.etudiantId() == 5L).toList();
        assertThat(lina).allSatisfy(l -> {
            assertThat(l.risque().niveau()).isEqualTo("eleve");
            assertThat(l.profil()).isEqualTo("Décrocheur");
        });
        var sami = r.lignes().stream().filter(l -> l.etudiantId() == 6L).findFirst().orElseThrow();
        assertThat(sami.risque()).isNull();
        assertThat(sami.risqueIndisponible()).isEqualTo(IaApprenantService.DONNEES_INCOMPLETES);
        assertThat(r.lignes().get(0).coursTitre()).isEqualTo("SQL");
        assertThat(r.inscriptions().statut()).isEqualTo(Statut.DISPONIBLE);
        assertThat(r.donneesSimulees()).isTrue();
        // Un seul apprenant complet : un seul vecteur envoyé, sans aucun identifiant.
        verify(aiClient).risque(eq(JETON), argThat(lot -> lot.apprenants().size() == 1));
    }

    @Test
    void routeDesInscriptionsAbsente_messageClair() {
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(CATALOGUE));
        when(coursClient.inscriptionsDuCours(10L)).thenThrow(statut(400));

        ApprenantsResponse r = service.pourFormateur(FORMATEUR, JETON);

        assertThat(r.lignes()).isEmpty();
        assertThat(r.inscriptions().statut()).isEqualTo(Statut.PAS_ENCORE_DISPONIBLE);
        assertThat(r.message()).contains("contrat 7");
        assertThat(r.cours()).hasSize(2);
        verify(coursClient, never()).inscriptionsDuCours(11L);
    }

    @Test
    void aucunCours() {
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(List.of(CATALOGUE.get(2))));

        ApprenantsResponse r = service.pourFormateur(FORMATEUR, JETON);

        assertThat(r.cours()).isEmpty();
        assertThat(r.message()).isEqualTo("Tu n'as pas encore de cours.");
    }

    @Test
    void iaArretee_lesLignesRestent() {
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(List.of(CATALOGUE.get(0))));
        when(coursClient.inscriptionsDuCours(10L)).thenReturn(ApiResponse.ok(List.of(inscription(5, 10, 0.3))));
        when(userRepository.findById(5L)).thenReturn(Optional.of(apprenant(5, "Lina")));
        when(indicateursService.calculer(5L)).thenReturn(indicateurs(true));
        when(aiClient.risque(anyString(), any())).thenThrow(new RuntimeException("Connection refused"));

        ApprenantsResponse r = service.pourFormateur(FORMATEUR, JETON);

        assertThat(r.lignes()).hasSize(1);
        assertThat(r.lignes().get(0).risqueIndisponible()).contains("indisponible");
    }

    @Test
    void lAdminVoitTousLesCours() {
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(CATALOGUE));
        when(coursClient.inscriptionsDuCours(anyLong())).thenReturn(ApiResponse.ok(List.of()));

        ApprenantsResponse r = service.pourFormateur(new CurrentUser(1L, CurrentUser.ADMIN), JETON);

        assertThat(r.cours()).hasSize(3);
        assertThat(r.lignes()).isEmpty();
        verify(aiClient, never()).risque(anyString(), any());
    }
}

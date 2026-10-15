package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.bawsla.user.client.AiClient;
import tn.esprit.bawsla.user.client.CoursClient;
import tn.esprit.bawsla.user.client.CoursDto;
import tn.esprit.bawsla.user.client.ProfilIaDto;
import tn.esprit.bawsla.user.client.RecommandationsIaDto;
import tn.esprit.bawsla.user.client.RisqueIaDto;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.user.common.exception.UnauthorizedException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.IaApprenantResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Indicateurs;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Source;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Statut;

@ExtendWith(MockitoExtension.class)
class IaApprenantServiceTest {

    private static final String JETON = "Bearer abc";
    private static final CurrentUser APPRENANT = new CurrentUser(5L, CurrentUser.ETUDIANT);
    private static final CurrentUser FORMATEUR = new CurrentUser(2L, CurrentUser.ENSEIGNANT);

    @Mock private IndicateursService indicateursService;
    @Mock private CoursClient coursClient;
    @Mock private AiClient aiClient;
    @InjectMocks private IaApprenantService service;

    private static final Indicateurs COMPLETS = new Indicateurs(0.75, 0.7, 4, 2, 5, 0.6, 400, 2, 1, 12, 1);
    private static final Indicateurs PARTIELS = new Indicateurs(null, null, null, null, null, null, null, null, null, 3, 0);

    private static IndicateursResponse indicateurs(Indicateurs i, boolean complet) {
        return new IndicateursResponse(i, List.of(10L), List.of(10L), List.of("Données"), Map.of(), complet);
    }

    private static final List<CoursDto> CATALOGUE = List.of(
            new CoursDto(10L, "SQL", "Données", "DEBUTANT", "PUBLIE", 2L),
            new CoursDto(11L, "Brouillon", "Données", "AVANCE", "BROUILLON", 2L),
            new CoursDto(12L, "React", "Web", "DEBUTANT", "PUBLIE", 3L));

    private static ApiResponse<RecommandationsIaDto> recos() {
        return ApiResponse.ok(new RecommandationsIaDto(
                List.of(new RecommandationsIaDto.CoursRecommande(12L, "React", 0.5, "Nouveau dans le catalogue")), true));
    }

    @Test
    void donneesCompletes_profilEtRecommandations_jamaisLeRisque() {
        when(indicateursService.lesMiens(APPRENANT)).thenReturn(indicateurs(COMPLETS, true));
        when(aiClient.profil(eq(JETON), any())).thenReturn(ApiResponse.ok(
                new ProfilIaDto(List.of(new ProfilIaDto.Groupe(1, "Régulier", "…")), true)));
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(CATALOGUE));
        when(aiClient.recommandations(eq(JETON), any())).thenReturn(recos());

        IaApprenantResponse r = service.pourMoi(APPRENANT, JETON);

        assertThat(r.profil().nom()).isEqualTo("Régulier");
        assertThat(r.recommandations()).hasSize(1);
        assertThat(r.risque()).isNull();
        assertThat(r.donneesSimulees()).isTrue();
        assertThat(r.indicateurs().noteMoyenne()).isEqualTo(0.75);
        verify(aiClient, never()).risque(anyString(), any());
        // Seuls les cours publiés partent vers l'IA, avec les indicateurs de l'apprenant (sans son identifiant).
        verify(aiClient).recommandations(eq(JETON), argThat(c ->
                c.coursDisponibles().stream().map(x -> x.coursId()).toList().equals(List.of(10L, 12L))
                        && c.coursSuivis().equals(List.of(10L)) && c.nombre() == IaApprenantService.NB_RECOMMANDATIONS));
        verify(aiClient).profil(eq(JETON), argThat(l -> l.apprenants().get(0).noteMoyenne() == 0.75));
    }

    @Test
    void donneesIncompletes_pasDeProfil_maisDesRecommandations() {
        when(indicateursService.lesMiens(APPRENANT)).thenReturn(indicateurs(PARTIELS, false));
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(CATALOGUE));
        when(aiClient.recommandations(eq(JETON), any())).thenReturn(recos());

        IaApprenantResponse r = service.pourMoi(APPRENANT, JETON);

        assertThat(r.profil()).isNull();
        assertThat(r.profilIndisponible()).isEqualTo(IaApprenantService.DONNEES_INCOMPLETES);
        assertThat(r.recommandations()).hasSize(1);
        verify(aiClient, never()).profil(anyString(), any());
    }

    @Test
    void iaArretee_messageClair_pasDErreur() {
        when(indicateursService.lesMiens(APPRENANT)).thenReturn(indicateurs(COMPLETS, true));
        when(aiClient.profil(eq(JETON), any())).thenThrow(new ServiceUnavailableException("503"));
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(CATALOGUE));
        when(aiClient.recommandations(eq(JETON), any())).thenThrow(new RuntimeException("Connection refused"));

        IaApprenantResponse r = service.pourMoi(APPRENANT, JETON);

        assertThat(r.profilIndisponible()).contains("indisponible");
        assertThat(r.recommandationsIndisponibles()).contains("indisponible");
    }

    @Test
    void catalogueVideOuIndisponible() {
        when(indicateursService.lesMiens(APPRENANT)).thenReturn(indicateurs(PARTIELS, false));
        when(coursClient.catalogue()).thenReturn(ApiResponse.ok(List.of()));
        assertThat(service.pourMoi(APPRENANT, JETON).recommandationsIndisponibles()).contains("Aucun cours publié");

        when(coursClient.catalogue()).thenThrow(new RuntimeException("cours-service arrêté"));
        assertThat(service.pourMoi(APPRENANT, JETON).recommandationsIndisponibles()).contains("catalogue");
    }

    @Test
    void sansJeton_401() {
        assertThatThrownBy(() -> service.pourMoi(APPRENANT, null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void formateur_risqueEtProfil_siDonneesCompletes() {
        when(indicateursService.dUnApprenant(FORMATEUR, 5L)).thenReturn(indicateurs(COMPLETS, true));
        when(aiClient.risque(eq(JETON), any())).thenReturn(ApiResponse.ok(new RisqueIaDto(
                List.of(new RisqueIaDto.Resultat(0.82, "eleve", List.of("Note moyenne basse aux quiz (40%)"))), 0.4, true)));
        when(aiClient.profil(eq(JETON), any())).thenReturn(ApiResponse.ok(
                new ProfilIaDto(List.of(new ProfilIaDto.Groupe(2, "Débutant en difficulté", "…")), true)));

        IaApprenantResponse r = service.pourUnApprenant(FORMATEUR, 5L, JETON);

        assertThat(r.risque().niveau()).isEqualTo("eleve");
        assertThat(r.profil().nom()).isEqualTo("Débutant en difficulté");
    }

    @Test
    void formateur_donneesIncompletes_aucunePrediction() {
        when(indicateursService.dUnApprenant(FORMATEUR, 5L)).thenReturn(indicateurs(PARTIELS, false));

        IaApprenantResponse r = service.pourUnApprenant(FORMATEUR, 5L, JETON);

        assertThat(r.risque()).isNull();
        assertThat(r.risqueIndisponible()).isEqualTo(IaApprenantService.DONNEES_INCOMPLETES);
        verify(aiClient, never()).risque(anyString(), any());
    }

    @Test
    void donneesIncompletes_nommeSeulementCeQuiManque() {
        Source ok = new Source("x", "c", Statut.DISPONIBLE, null);
        Source absente = new Source("x", "c", Statut.PAS_ENCORE_DISPONIBLE, null);

        assertThat(IaApprenantService.donneesIncompletes(Map.of("tentatives", ok, "inscriptions", ok, "tempsPasse", absente)))
                .isEqualTo("Pas encore assez de données : le temps passé dans les cours n'est pas encore disponible.");
        assertThat(IaApprenantService.donneesIncompletes(Map.of("tentatives", absente, "inscriptions", absente)))
                .isEqualTo("Pas encore assez de données : les résultats aux quiz et les inscriptions aux cours ne sont pas encore disponibles.");
        assertThat(IaApprenantService.donneesIncompletes(Map.of())).isEqualTo(IaApprenantService.DONNEES_INCOMPLETES);
    }
}

package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import feign.FeignException;
import tn.esprit.bawsla.user.client.CoursClient;
import tn.esprit.bawsla.user.client.InscriptionDto;
import tn.esprit.bawsla.user.client.NotionRateeDto;
import tn.esprit.bawsla.user.client.QuizClient;
import tn.esprit.bawsla.user.client.TentativeDto;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.exception.ForbiddenException;
import tn.esprit.bawsla.user.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.IndicateursResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Statut;
import tn.esprit.bawsla.user.entity.Parcours;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class IndicateursServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    private static final Instant MAINTENANT = Instant.parse("2026-10-15T10:00:00Z");
    private static final CurrentUser APPRENANT = new CurrentUser(5L, CurrentUser.ETUDIANT);
    private static final CurrentUser FORMATEUR = new CurrentUser(2L, CurrentUser.ENSEIGNANT);

    @Mock private UserRepository userRepository;
    @Mock private ActiviteService activiteService;
    @Mock private QuizClient quizClient;
    @Mock private CoursClient coursClient;

    private IndicateursService service;
    private User apprenant;

    @BeforeEach
    void setUp() {
        service = new IndicateursService(userRepository, activiteService, quizClient, coursClient, Clock.fixed(MAINTENANT, ZONE));
        apprenant = new User();
        apprenant.setId(5L);
        apprenant.setRole(Role.ETUDIANT);
        apprenant.enregistrerConnexion(LocalDateTime.ofInstant(MAINTENANT, ZONE).minusDays(3));
        Parcours p = new Parcours();
        p.setDomaines(List.of("Data", "Web"));
        apprenant.setParcours(p);
        // lenient : certains tests (droits d'accès) s'arrêtent avant de lire l'apprenant.
        lenient().when(userRepository.findById(5L)).thenReturn(Optional.of(apprenant));
    }

    private static FeignException notFound() {
        return statut(404);
    }

    /** Vraie exception (sous-classe anonyme) : un mock créé pendant un autre stubbing troublerait Mockito. */
    private static FeignException statut(int code) {
        return new FeignException(code, "réponse " + code) { };
    }

    private void toutesLesSources() {
        when(quizClient.tentatives(5L)).thenReturn(ApiResponse.ok(List.of(
                new TentativeDto(1L, 10L, 6, 10, 60, true, null),
                new TentativeDto(2L, 10L, 9, 10, 90, true, null))));
        when(quizClient.notionsRatees(5L)).thenReturn(ApiResponse.ok(List.of(
                new NotionRateeDto("Jointures", 10L, 3, 0.4), new NotionRateeDto("Index", 11L, 1, 0.8))));
        when(coursClient.inscriptions(5L)).thenReturn(ApiResponse.ok(List.of(
                new InscriptionDto(5L, 10L, null, 0.5, 120, false, null),
                new InscriptionDto(5L, 11L, null, 1.0, 300, true, null))));
        when(activiteService.joursActifs(5L)).thenReturn(12);
    }

    @Test
    void toutesLesSourcesDisponibles_indicateursComplets() {
        toutesLesSources();

        IndicateursResponse r = service.lesMiens(APPRENANT);

        var i = r.indicateurs();
        assertThat(r.complet()).isTrue();
        assertThat(i.nbQuiz()).isEqualTo(2);
        assertThat(i.noteMoyenne()).isEqualTo(0.75);
        assertThat(i.tauxReussite()).isEqualTo(0.75);
        assertThat(i.nbNotionsRatees()).isEqualTo(2);
        assertThat(i.nbErreurs()).isEqualTo(4);
        assertThat(i.nbCoursSuivis()).isEqualTo(2);
        assertThat(i.progressionMoyenne()).isEqualTo(0.75);
        assertThat(i.tempsPasseMinutes()).isEqualTo(420);
        assertThat(i.nbCoursTermines()).isEqualTo(1);
        assertThat(i.joursActifs30j()).isEqualTo(12);
        assertThat(i.joursDepuisDerniereConnexion()).isEqualTo(3);
        assertThat(r.coursSuivis()).containsExactly(10L, 11L);
        assertThat(r.coursEnDifficulte()).containsExactly(10L, 11L);
        assertThat(r.domaines()).containsExactly("Data", "Web");
        assertThat(r.sources().values()).allMatch(s -> s.statut() == Statut.DISPONIBLE);
        verify(activiteService).enregistrer(5L);
    }

    @Test
    void routeAbsente_indicateursNullEtStatutExplicite_lesAutresSontGardes() {
        when(quizClient.tentatives(5L)).thenThrow(notFound());
        when(quizClient.notionsRatees(5L)).thenReturn(ApiResponse.ok(List.of(new NotionRateeDto("Index", 11L, 2, 0.5))));
        when(coursClient.inscriptions(5L)).thenThrow(notFound());

        IndicateursResponse r = service.lesMiens(APPRENANT);

        assertThat(r.complet()).isFalse();
        assertThat(r.indicateurs().noteMoyenne()).isNull();
        assertThat(r.indicateurs().nbCoursSuivis()).isNull();
        assertThat(r.indicateurs().nbNotionsRatees()).isEqualTo(1);
        assertThat(r.sources().get("tentatives").statut()).isEqualTo(Statut.PAS_ENCORE_DISPONIBLE);
        assertThat(r.sources().get("inscriptions").statut()).isEqualTo(Statut.PAS_ENCORE_DISPONIBLE);
        assertThat(r.sources().get("notionsRatees").statut()).isEqualTo(Statut.DISPONIBLE);
    }

    @Test
    void cheminCaptureParIdDuFournisseur_400_comptePourRouteAbsente() {
        when(quizClient.tentatives(5L)).thenThrow(statut(400));
        when(quizClient.notionsRatees(5L)).thenThrow(statut(405));
        when(coursClient.inscriptions(5L)).thenThrow(statut(503));

        IndicateursResponse r = service.lesMiens(APPRENANT);

        assertThat(r.sources().get("tentatives").statut()).isEqualTo(Statut.PAS_ENCORE_DISPONIBLE);
        assertThat(r.sources().get("notionsRatees").statut()).isEqualTo(Statut.PAS_ENCORE_DISPONIBLE);
        assertThat(r.sources().get("inscriptions").statut()).isEqualTo(Statut.INDISPONIBLE);
    }

    @Test
    void routeQuiRenvoieUnBouchon_marqueeStub() {
        when(quizClient.tentatives(5L)).thenThrow(notFound());
        when(quizClient.notionsRatees(5L)).thenReturn(ApiResponse.ok(List.of(), "stub"));
        when(coursClient.inscriptions(5L)).thenThrow(notFound());

        IndicateursResponse r = service.lesMiens(APPRENANT);

        assertThat(r.sources().get("notionsRatees").statut()).isEqualTo(Statut.STUB);
        assertThat(r.indicateurs().nbNotionsRatees()).isNull();
    }

    @Test
    void serviceArrete_statutIndisponible_sansErreur() {
        when(quizClient.tentatives(5L)).thenThrow(new RuntimeException("Connection refused"));
        when(quizClient.notionsRatees(5L)).thenThrow(new RuntimeException("Connection refused"));
        when(coursClient.inscriptions(5L)).thenThrow(new RuntimeException("Connection refused"));

        IndicateursResponse r = service.lesMiens(APPRENANT);

        assertThat(r.sources().get("tentatives").statut()).isEqualTo(Statut.INDISPONIBLE);
        assertThat(r.indicateurs().joursDepuisDerniereConnexion()).isEqualTo(3);
    }

    @Test
    void aucunQuizNiCours_zerosReels_pasNull() {
        when(quizClient.tentatives(5L)).thenReturn(ApiResponse.ok(List.of()));
        when(quizClient.notionsRatees(5L)).thenReturn(ApiResponse.ok(List.of()));
        when(coursClient.inscriptions(5L)).thenReturn(ApiResponse.ok(List.of()));

        var i = service.lesMiens(APPRENANT).indicateurs();

        assertThat(i.nbQuiz()).isZero();
        assertThat(i.noteMoyenne()).isZero();
        assertThat(i.nbCoursSuivis()).isZero();
    }

    @Test
    void unApprenantNePeutPasLireCeuxDUnAutre() {
        assertThatThrownBy(() -> service.dUnApprenant(APPRENANT, 9L)).isInstanceOf(ForbiddenException.class);
        verify(quizClient, never()).tentatives(any());
    }

    @Test
    void leFormateurLitCeuxDUnApprenant_maisPasDUnFormateur() {
        toutesLesSources();
        assertThat(service.dUnApprenant(FORMATEUR, 5L).complet()).isTrue();

        User autreFormateur = new User();
        autreFormateur.setId(8L);
        autreFormateur.setRole(Role.ENSEIGNANT);
        when(userRepository.findById(8L)).thenReturn(Optional.of(autreFormateur));
        assertThatThrownBy(() -> service.dUnApprenant(FORMATEUR, 8L)).isInstanceOf(ResourceNotFoundException.class);
    }
}

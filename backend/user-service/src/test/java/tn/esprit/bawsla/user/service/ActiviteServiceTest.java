package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import tn.esprit.bawsla.user.entity.ActiviteJour;
import tn.esprit.bawsla.user.repository.ActiviteJourRepository;

@ExtendWith(MockitoExtension.class)
class ActiviteServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 15);

    @Mock
    private ActiviteJourRepository repository;

    private ActiviteService service() {
        return new ActiviteService(repository, Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZONE));
    }

    @Test
    void premiereActiviteDuJour_enregistree() {
        service().enregistrer(5L);
        verify(repository).save(any(ActiviteJour.class));
    }

    @Test
    void dejaActifAujourdhui_rienDePlus() {
        when(repository.existsByUserIdAndJour(5L, AUJOURDHUI)).thenReturn(true);
        service().enregistrer(5L);
        verify(repository, never()).save(any());
    }

    @Test
    void doublonConcurrent_sansErreur() {
        when(repository.save(any(ActiviteJour.class))).thenThrow(new DataIntegrityViolationException("unique"));
        service().enregistrer(5L);
    }

    @Test
    void joursActifs_fenetreDe30JoursAujourdhuiCompris() {
        when(repository.countByUserIdAndJourGreaterThanEqual(5L, AUJOURDHUI.minusDays(29))).thenReturn(12L);
        assertThat(service().joursActifs(5L)).isEqualTo(12);
    }
}

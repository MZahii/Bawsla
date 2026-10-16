package tn.esprit.bawsla.user.client;

import java.time.LocalDateTime;

/**
 * Contrat 7 : une inscription (progression de 0 à 1). {@code tempsPasseMinutes} est absent (null) tant que
 * cours-service ne mesure pas le temps passé : ce n'est surtout pas 0.
 */
public record InscriptionDto(Long etudiantId, Long coursId, LocalDateTime dateInscription, double progression,
                             Integer tempsPasseMinutes, boolean termine, LocalDateTime dateDerniereActivite) {
}

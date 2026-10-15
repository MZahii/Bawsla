package tn.esprit.bawsla.user.client;

import java.time.LocalDateTime;

/** Contrat 7 : une inscription (progression de 0 à 1). */
public record InscriptionDto(Long etudiantId, Long coursId, LocalDateTime dateInscription, double progression,
                             int tempsPasseMinutes, boolean termine, LocalDateTime dateDerniereActivite) {
}

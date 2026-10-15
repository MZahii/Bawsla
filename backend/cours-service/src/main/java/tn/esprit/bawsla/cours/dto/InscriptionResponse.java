package tn.esprit.bawsla.cours.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.cours.entity.Inscription;

/**
 * Inscription d'un apprenant à un cours (contrat 7 de CONTRATS_API.md, tel que demandé par le module User).
 * {@code progression} va de 0 à 1 (chapitres terminés ÷ chapitres du cours) ; {@code termine} vaut true quand tous les chapitres le sont.
 * {@code tempsPasseMinutes} et {@code dateDerniereActivite} sont OMIS tant qu'ils n'existent pas (jamais un 0 trompeur : le module User
 * en déduirait que l'apprenant ne travaille pas). Aucune donnée personnelle : seulement l'identifiant de l'apprenant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InscriptionResponse(Long etudiantId, Long coursId, LocalDateTime dateInscription, double progression,
                                  Integer tempsPasseMinutes, boolean termine, LocalDateTime dateDerniereActivite) {

    public static InscriptionResponse from(Inscription i, int chapitresTermines, int chapitresTotal) {
        boolean termine = chapitresTotal > 0 && chapitresTermines >= chapitresTotal;
        double progression = chapitresTotal == 0 ? 0 : Math.min(1.0, Math.round(10000.0 * chapitresTermines / chapitresTotal) / 10000.0);
        return new InscriptionResponse(i.getEtudiantId(), i.getCoursId(), secondes(i.getDateInscription()), progression,
                i.getTempsPasseMinutes(), termine, secondes(i.getDateDerniereActivite()));
    }

    private static LocalDateTime secondes(LocalDateTime date) {
        return date == null ? null : date.truncatedTo(ChronoUnit.SECONDS);
    }
}

package tn.esprit.bawsla.cours.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Progression de l'apprenant connecté dans un cours : les champs de {@link InscriptionResponse} (contrat 7, inchangé) PLUS la liste des
 * chapitres terminés, dont le lecteur a besoin pour cocher le plan et proposer « Marquer ce chapitre comme terminé » ou « Annuler ».
 * Réservée à l'apprenant lui-même : le contrat 7, lui, ne donne que la proportion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgressionResponse(Long etudiantId, Long coursId, LocalDateTime dateInscription, double progression,
                                  Integer tempsPasseMinutes, boolean termine, LocalDateTime dateDerniereActivite,
                                  List<Long> chapitresTermines) {

    public static ProgressionResponse from(InscriptionResponse i, List<Long> chapitresTermines) {
        return new ProgressionResponse(i.etudiantId(), i.coursId(), i.dateInscription(), i.progression(), i.tempsPasseMinutes(),
                i.termine(), i.dateDerniereActivite(), chapitresTermines);
    }
}

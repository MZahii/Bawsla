package tn.esprit.bawsla.cours.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.cours.entity.ExplicationAudio;
import tn.esprit.bawsla.cours.entity.StatutFiche;

/**
 * Explication orale d'un cours dans une langue et un mode. L'apprenant reçoit la dernière version VALIDEE (script, page source,
 * audio à jour) ; score, alertes, modèle, version du prompt et verdicts par segment sont réservés au formateur et à l'ADMIN.
 *
 * <p>{@code aAudio} : un audio peut être lu. Pour l'apprenant, c'est seulement un audio correspondant au script actuel.
 * Pour le formateur, c'est l'aperçu le plus récent, même s'il date d'avant une modification ({@code audioAJour} le précise).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExplicationResponse(Long id, String langue, String mode, StatutFiche statut, List<SegmentExplicationResponse> segments,
                                  boolean aAudio, String fournisseurAudio, Long dureeMs, boolean minutageEstime,
                                  LocalDateTime dateGeneration, LocalDateTime dateValidation,
                                  Integer scoreFidelite, List<String> alertes, String versionPrompt, String modele, Integer nbMots,
                                  Integer dureeEstimeeSecondes, Boolean modifieeParFormateur, Boolean notionsChoisiesParIa,
                                  Boolean aRetraduire, Boolean audioAJour, Boolean aPuEcouter, Boolean valideeDisponible) {

    public static ExplicationResponse from(ExplicationAudio e, boolean gestionnaire, boolean valideeDisponible) {
        List<SegmentExplicationResponse> segments = e.getSegments().stream().map(s -> SegmentExplicationResponse.from(s, gestionnaire)).toList();
        boolean aAudio = gestionnaire ? e.aUnAudio() : e.audioAJour();
        return new ExplicationResponse(e.getId(), e.getLangue().code(), e.getMode().code(), e.getStatut(), segments,
                aAudio, aAudio ? e.getAudioFournisseur() : null, aAudio ? e.getDureeMs() : null, e.isMinutageEstime(),
                e.getDateGeneration(), e.getDateValidation(),
                gestionnaire ? e.getScoreFidelite() : null,
                gestionnaire ? List.copyOf(e.getAlertes()) : null,
                gestionnaire ? e.getVersionPrompt() : null,
                gestionnaire ? e.getModele() : null,
                gestionnaire ? e.getNbMots() : null,
                gestionnaire ? e.getDureeEstimeeSecondes() : null,
                gestionnaire ? e.isModifieeParFormateur() : null,
                gestionnaire ? e.isNotionsChoisiesParIa() : null,
                gestionnaire ? e.isARetraduire() : null,
                gestionnaire ? e.audioAJour() : null,
                gestionnaire ? e.aUnAudio() : null,
                gestionnaire ? valideeDisponible : null);
    }
}

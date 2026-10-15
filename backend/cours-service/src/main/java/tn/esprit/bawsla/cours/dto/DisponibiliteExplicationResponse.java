package tn.esprit.bawsla.cours.dto;

import tn.esprit.bawsla.cours.entity.ExplicationAudio;
import tn.esprit.bawsla.cours.entity.StatutFiche;

/** Une combinaison langue x mode qui existe pour un cours (validée pour l'apprenant ; la plus récente pour le formateur). */
public record DisponibiliteExplicationResponse(String langue, String mode, StatutFiche statut, boolean aAudio, Long dureeMs) {

    public static DisponibiliteExplicationResponse from(ExplicationAudio e, boolean gestionnaire) {
        boolean aAudio = gestionnaire ? e.aUnAudio() : e.audioAJour();
        return new DisponibiliteExplicationResponse(e.getLangue().code(), e.getMode().code(), e.getStatut(), aAudio,
                aAudio ? e.getDureeMs() : null);
    }
}

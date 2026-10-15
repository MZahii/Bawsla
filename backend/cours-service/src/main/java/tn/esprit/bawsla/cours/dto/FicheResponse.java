package tn.esprit.bawsla.cours.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.cours.entity.FicheRevision;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.entity.StatutFiche;

/**
 * Fiche de révision. L'apprenant reçoit le contenu de la dernière fiche VALIDEE ; les champs de contrôle
 * (score, alertes, modèle, version du prompt, notions suspectes) sont réservés au formateur et à l'ADMIN.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FicheResponse(Long id, StatutFiche statut, String resume, List<String> motsCles, List<NotionResponse> notions,
                            Niveau niveau, boolean aAudio, String fournisseurAudio, LocalDateTime dateGeneration, LocalDateTime dateValidation,
                            Integer scoreFidelite, List<String> alertes, String versionPrompt, String modele,
                            Boolean modifieeParFormateur, Boolean valideeDisponible) {

    /** {@code fournisseurAudio} (ElevenLabs, Gemini…) est montré à tous : l'interface indique qui a généré la voix. */
    public static FicheResponse from(FicheRevision f, boolean aAudio, String fournisseurAudio, boolean gestionnaire,
                                     boolean valideeDisponible) {
        List<NotionResponse> notions = f.getNotions().stream().map(n -> NotionResponse.from(n, gestionnaire)).toList();
        return new FicheResponse(f.getId(), f.getStatut(), f.getResume(), List.copyOf(f.getMotsCles()), notions, f.getNiveau(),
                aAudio, fournisseurAudio, f.getDateGeneration(), f.getDateValidation(),
                gestionnaire ? f.getScoreFidelite() : null,
                gestionnaire ? List.copyOf(f.getAlertes()) : null,
                gestionnaire ? f.getVersionPrompt() : null,
                gestionnaire ? f.getModele() : null,
                gestionnaire ? f.isModifieeParFormateur() : null,
                gestionnaire ? valideeDisponible : null);
    }
}

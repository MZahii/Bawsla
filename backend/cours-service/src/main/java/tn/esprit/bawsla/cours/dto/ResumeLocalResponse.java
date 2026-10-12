package tn.esprit.bawsla.cours.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.cours.entity.PhraseResumeLocal;
import tn.esprit.bawsla.cours.entity.ResumeLocal;
import tn.esprit.bawsla.cours.entity.StatutResumeLocal;

/**
 * Résumé automatique local d'un cours. L'apprenant reçoit la dernière version VALIDE : texte, phrases (avec leur page) et audio.
 * Score de fidélité, alertes, modèle, extraits sources, verdict par phrase et durée de calcul sont réservés au formateur et à l'ADMIN.
 *
 * <p>{@code aAudio} : un audio peut être lu. {@code valideeDisponible} : une version validée existe (visible des apprenants), même si la
 * plus récente est encore un brouillon.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResumeLocalResponse(Long id, StatutResumeLocal statut, String langue, String modeUtilise, String texte,
                                  List<Phrase> phrases, boolean aAudio, Long dureeAudio, String fournisseurAudio,
                                  LocalDateTime dateCreation, LocalDateTime dateValidation,
                                  String modele, Integer scoreFidelite, Integer scoreReformule, List<String> alertes,
                                  Long dureeCalculMs, Boolean valideeDisponible) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Phrase(int ordre, String texte, Integer page, String extraitSource, Boolean justifiee) {

        static Phrase from(PhraseResumeLocal p, boolean gestionnaire) {
            return new Phrase(p.getOrdre(), p.getTexte(), p.getPage(), gestionnaire ? p.getExtrait() : null,
                    gestionnaire ? p.isJustifiee() : null);
        }
    }

    public static ResumeLocalResponse from(ResumeLocal r, boolean gestionnaire, boolean valideeDisponible) {
        return new ResumeLocalResponse(r.getId(), r.getStatut(), r.getLangue(), r.getModeUtilise(), r.getTexte(),
                r.getPhrases().stream().map(p -> Phrase.from(p, gestionnaire)).toList(),
                r.aUnAudio(), r.aUnAudio() ? r.getDureeAudio() : null, r.aUnAudio() ? r.getFournisseurAudio() : null,
                r.getDateCreation(), r.getDateValidation(),
                gestionnaire ? r.getModele() : null,
                gestionnaire ? r.getScoreFidelite() : null,
                gestionnaire ? r.getScoreReformule() : null,
                gestionnaire ? List.copyOf(r.getAlertes()) : null,
                gestionnaire ? r.getDureeCalculMs() : null,
                gestionnaire ? valideeDisponible : null);
    }
}

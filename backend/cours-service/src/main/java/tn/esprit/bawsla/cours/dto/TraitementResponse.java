package tn.esprit.bawsla.cours.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.entity.TraitementIA;

/** Suivi du dernier lancement de l'IA sur un cours. {@code enCours} reste vrai tant qu'une étape n'est pas terminée. */
public record TraitementResponse(String execution, boolean enCours, List<Etape> etapes) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Etape(EtapeIA etape, StatutTraitement statut, Long dureeMs, String erreur) {

        static Etape from(TraitementIA t) {
            return new Etape(t.getEtape(), t.getStatut(), t.getDureeMs(), t.getErreur());
        }
    }

    public static TraitementResponse aucun() {
        return new TraitementResponse(null, false, List.of());
    }

    public static TraitementResponse from(String execution, List<TraitementIA> lignes) {
        return new TraitementResponse(execution, lignes.stream().anyMatch(l -> l.getStatut().actif()),
                lignes.stream().map(Etape::from).toList());
    }
}

package tn.esprit.bawsla.user.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Indicateurs d'un apprenant pour le Machine Learning, et état de chaque source de données.
 * Un indicateur est null quand sa source n'est pas encore disponible : on ne remplace jamais une donnée absente
 * par un faux zéro (dans le JSON, le champ est alors omis, règle commune du projet).
 * {@code complet} vaut true seulement si toutes les sources ont répondu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndicateursResponse(Indicateurs indicateurs, List<Long> coursSuivis, List<Long> coursEnDifficulte,
                                  List<String> domaines, Map<String, Source> sources, boolean complet) {

    /** Les 11 indicateurs du contrat 5 (module User), mêmes noms que côté ai-service. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Indicateurs(Double noteMoyenne, Double tauxReussite, Integer nbQuiz, Integer nbNotionsRatees,
                              Integer nbErreurs, Double progressionMoyenne, Integer tempsPasseMinutes,
                              Integer nbCoursSuivis, Integer nbCoursTermines, Integer joursActifs30j,
                              Integer joursDepuisDerniereConnexion) {
    }

    public enum Statut {
        /** La source a répondu avec de vraies données (éventuellement une liste vide). */
        DISPONIBLE,
        /** La route existe mais renvoie encore un bouchon (message « stub »). */
        STUB,
        /** La route n'existe pas encore dans le service fournisseur (404) : contrat proposé, pas implémenté. */
        PAS_ENCORE_DISPONIBLE,
        /** Le service n'a pas répondu (arrêté, erreur, délai). */
        INDISPONIBLE
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Source(String fournisseur, String contrat, Statut statut, String detail) {
    }
}

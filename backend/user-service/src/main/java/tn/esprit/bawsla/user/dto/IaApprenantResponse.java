package tn.esprit.bawsla.user.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.user.client.ProfilIaDto;
import tn.esprit.bawsla.user.client.RecommandationsIaDto;
import tn.esprit.bawsla.user.client.RisqueIaDto;

/**
 * Résultats du Machine Learning pour un apprenant. Chaque partie peut manquer : son champ « …Indisponible » dit
 * alors pourquoi (données encore incomplètes, catalogue vide, IA arrêtée). Jamais de prédiction sur des données
 * incomplètes : le profil et le risque demandent toutes les sources ; les recommandations, seulement le catalogue.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IaApprenantResponse(
        ProfilIaDto.Groupe profil,
        String profilIndisponible,
        RisqueIaDto.Resultat risque,
        String risqueIndisponible,
        List<RecommandationsIaDto.CoursRecommande> recommandations,
        String recommandationsIndisponibles,
        Boolean donneesSimulees,
        Map<String, IndicateursResponse.Source> sources,
        boolean complet,
        IndicateursResponse.Indicateurs indicateurs) {
}

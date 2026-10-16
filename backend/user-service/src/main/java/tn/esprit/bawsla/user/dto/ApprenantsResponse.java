package tn.esprit.bawsla.user.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.user.client.RisqueIaDto;

/**
 * Page Apprenants du formateur : une ligne par inscription (apprenant × cours) à ses cours, avec le risque d'échec
 * prédit par le Random Forest quand les données de l'apprenant sont complètes. Jamais visible par un apprenant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprenantsResponse(List<CoursResume> cours, List<Ligne> lignes, IndicateursResponse.Source inscriptions,
                                 String message, Boolean donneesSimulees) {

    public record CoursResume(Long id, String titre) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ligne(Long etudiantId, String nom, String prenom, Long avatarVersion, Long coursId, String coursTitre,
                       LocalDateTime dateInscription, Double progression, Integer tempsPasseMinutes,
                       LocalDateTime dateDerniereActivite, RisqueIaDto.Resultat risque, String risqueIndisponible,
                       String profil) {
    }
}

package tn.esprit.bawsla.user.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Requêtes de l'IA générative (contrat 5, /api/ai/user/conseil, /explication-risque, /plan, /rapport-cohorte,
 * /domaines). Seulement des nombres, des libellés, des notions et des titres de cours : jamais de nom, d'email ni
 * d'identifiant d'utilisateur (ai-service refuse tout champ inattendu).
 */
public final class IaGenerativeDto {

    private IaGenerativeDto() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Conseil(IndicateursIaDto indicateurs, String profil, String objectif, List<String> coursSuivis,
                          List<String> notionsARevoir, List<String> coursRecommandes) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExplicationRisque(String niveau, double probabilite, List<String> facteurs,
                                    IndicateursIaDto indicateurs, String profil, List<String> notionsARevoir) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Plan(String tempsDisponible, String objectif, String profil, List<CoursEnCours> coursEnCours,
                       List<String> notionsARevoir, List<String> coursRecommandes) {
    }

    public record CoursEnCours(String titre, double progression) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cohorte(int nbApprenants, int nbCours, RepartitionRisque risques, Double progressionMoyenne,
                          Double noteMoyenne, List<Effectif> profils, List<Effectif> notions, List<CoursStat> cours) {
    }

    public record RepartitionRisque(int faible, int moyen, int eleve, int inconnu) {
    }

    public record Effectif(String nom, int nombre) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CoursStat(String titre, int nbInscrits, Double progressionMoyenne) {
    }

    public record Domaines(String texte, List<String> domainesDisponibles, List<String> objectifsDisponibles) {
    }
}

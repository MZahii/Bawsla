package tn.esprit.bawsla.user.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tn.esprit.bawsla.user.client.AiClient;
import tn.esprit.bawsla.user.client.CoursClient;
import tn.esprit.bawsla.user.client.CoursDto;
import tn.esprit.bawsla.user.client.IndicateursIaDto;
import tn.esprit.bawsla.user.client.LotIndicateursIaDto;
import tn.esprit.bawsla.user.client.ProfilIaDto;
import tn.esprit.bawsla.user.client.RecommandationsIaDto;
import tn.esprit.bawsla.user.client.RecommandationsIaRequestDto;
import tn.esprit.bawsla.user.client.RecommandationsIaRequestDto.CoursCatalogue;
import tn.esprit.bawsla.user.client.RisqueIaDto;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.exception.UnauthorizedException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.IaApprenantResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Indicateurs;

/**
 * Relie user-service au Machine Learning d'ai-service (contrat 5) : indicateurs de l'apprenant (IndicateursService),
 * vrai catalogue (cours-service), puis profil, risque et recommandations. Seuls des nombres, des identifiants de
 * cours et des domaines partent vers l'IA : jamais l'identifiant, le nom ni l'email de l'apprenant.
 */
@Service
public class IaApprenantService {

    private static final Logger log = LoggerFactory.getLogger(IaApprenantService.class);
    static final int NB_RECOMMANDATIONS = 3;
    static final String DONNEES_INCOMPLETES =
            "Pas encore assez de données : les résultats aux quiz et les inscriptions aux cours ne sont pas encore disponibles.";

    private final IndicateursService indicateursService;
    private final CoursClient coursClient;
    private final AiClient aiClient;

    public IaApprenantService(IndicateursService indicateursService, CoursClient coursClient, AiClient aiClient) {
        this.indicateursService = indicateursService;
        this.coursClient = coursClient;
        this.aiClient = aiClient;
    }

    /** Page Progrès de l'apprenant : son profil et ses recommandations (jamais son risque). */
    public IaApprenantResponse pourMoi(CurrentUser current, String authorization) {
        exigerJeton(authorization);
        IndicateursResponse ind = indicateursService.lesMiens(current);
        Boolean simulees = null;

        ProfilIaDto.Groupe profil = null;
        String profilIndisponible = null;
        if (!ind.complet()) {
            profilIndisponible = DONNEES_INCOMPLETES;
        } else {
            try {
                ProfilIaDto p = aiClient.profil(authorization, lot(ind.indicateurs())).data();
                profil = p.groupes().get(0);
                simulees = p.donneesSimulees();
            } catch (RuntimeException e) {
                profilIndisponible = iaIndisponible(e);
            }
        }

        List<RecommandationsIaDto.CoursRecommande> recos = null;
        String recosIndisponibles = null;
        try {
            List<CoursCatalogue> catalogue = catalogue();
            if (catalogue.isEmpty()) {
                recosIndisponibles = "Aucun cours publié pour l'instant.";
            } else {
                RecommandationsIaDto r = aiClient.recommandations(authorization, new RecommandationsIaRequestDto(
                        ind.coursSuivis(), ind.domaines(), ind.coursEnDifficulte(), catalogue, NB_RECOMMANDATIONS)).data();
                recos = r.recommandations();
                simulees = simulees == null ? r.donneesSimulees() : simulees;
            }
        } catch (CatalogueIndisponible e) {
            recosIndisponibles = "Le catalogue des cours est indisponible pour le moment.";
        } catch (RuntimeException e) {
            recosIndisponibles = iaIndisponible(e);
        }

        return new IaApprenantResponse(profil, profilIndisponible, null, null, recos, recosIndisponibles, simulees,
                ind.sources(), ind.complet());
    }

    /** Page Apprenants du formateur (ou admin) : risque d'échec d'un apprenant et son profil. */
    public IaApprenantResponse pourUnApprenant(CurrentUser current, Long etudiantId, String authorization) {
        exigerJeton(authorization);
        IndicateursResponse ind = indicateursService.dUnApprenant(current, etudiantId);
        if (!ind.complet()) {
            return new IaApprenantResponse(null, DONNEES_INCOMPLETES, null, DONNEES_INCOMPLETES, null, null, null,
                    ind.sources(), false);
        }
        LotIndicateursIaDto lot = lot(ind.indicateurs());
        try {
            RisqueIaDto risque = aiClient.risque(authorization, lot).data();
            ProfilIaDto profil = aiClient.profil(authorization, lot).data();
            return new IaApprenantResponse(profil.groupes().get(0), null, risque.resultats().get(0), null, null, null,
                    risque.donneesSimulees(), ind.sources(), true);
        } catch (RuntimeException e) {
            String raison = iaIndisponible(e);
            return new IaApprenantResponse(null, raison, null, raison, null, null, null, ind.sources(), true);
        }
    }

    /** Indicateurs complets → vecteur pour l'IA (les 11 nombres, sans aucun identifiant). */
    static LotIndicateursIaDto lot(Indicateurs i) {
        return new LotIndicateursIaDto(List.of(new IndicateursIaDto(
                i.noteMoyenne(), i.tauxReussite(), i.nbQuiz(), i.nbNotionsRatees(), i.nbErreurs(),
                i.progressionMoyenne(), i.tempsPasseMinutes(), i.nbCoursSuivis(), i.nbCoursTermines(),
                i.joursActifs30j(), i.joursDepuisDerniereConnexion())));
    }

    /** Cours publiés visibles par l'utilisateur courant, réduits à ce dont l'IA a besoin. */
    private List<CoursCatalogue> catalogue() {
        try {
            ApiResponse<List<CoursDto>> r = coursClient.catalogue();
            List<CoursDto> cours = r.data() == null ? List.of() : r.data();
            return cours.stream()
                    .filter(c -> c.statut() == null || "PUBLIE".equals(c.statut()))
                    .map(c -> new CoursCatalogue(c.id(), c.titre(), c.categorie(), c.niveau()))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Catalogue des cours indisponible : {}", e.getMessage());
            throw new CatalogueIndisponible();
        }
    }

    /** L'IA est jointe par la gateway, qui exige le JWT de l'utilisateur. */
    private static void exigerJeton(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Jeton d'authentification manquant");
        }
    }

    private static String iaIndisponible(RuntimeException e) {
        log.warn("IA du module User indisponible : {}", e.getMessage());
        return "Le service d'IA est indisponible pour le moment. Réessaie dans quelques minutes.";
    }

    private static final class CatalogueIndisponible extends RuntimeException {
    }
}

package tn.esprit.bawsla.user.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import feign.FeignException;
import tn.esprit.bawsla.user.client.AiClient;
import tn.esprit.bawsla.user.client.CoursClient;
import tn.esprit.bawsla.user.client.CoursDto;
import tn.esprit.bawsla.user.client.InscriptionDto;
import tn.esprit.bawsla.user.client.LotIndicateursIaDto;
import tn.esprit.bawsla.user.client.ProfilIaDto;
import tn.esprit.bawsla.user.client.RisqueIaDto;
import tn.esprit.bawsla.user.common.exception.UnauthorizedException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.ApprenantsResponse;
import tn.esprit.bawsla.user.dto.ApprenantsResponse.CoursResume;
import tn.esprit.bawsla.user.dto.ApprenantsResponse.Ligne;
import tn.esprit.bawsla.user.dto.IndicateursResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Source;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Statut;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

/**
 * Page Apprenants du formateur : les inscrits à SES cours (contrat 7, cours-service) et leur risque d'échec.
 * Un formateur ne voit jamais les apprenants des autres : la liste vient uniquement des inscriptions à ses cours.
 * Le risque est calculé en un seul appel à l'IA pour tous les apprenants aux données complètes.
 */
@Service
public class ApprenantsService {

    private static final Logger log = LoggerFactory.getLogger(ApprenantsService.class);
    private static final Set<Integer> ROUTE_ABSENTE = Set.of(400, 404, 405);
    static final String CONTRAT_7 = "contrat 7";

    private final CoursClient coursClient;
    private final IndicateursService indicateursService;
    private final AiClient aiClient;
    private final UserRepository userRepository;

    public ApprenantsService(CoursClient coursClient, IndicateursService indicateursService, AiClient aiClient,
                             UserRepository userRepository) {
        this.coursClient = coursClient;
        this.indicateursService = indicateursService;
        this.aiClient = aiClient;
        this.userRepository = userRepository;
    }

    public ApprenantsResponse pourFormateur(CurrentUser current, String authorization) {
        current.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Jeton d'authentification manquant");
        }

        List<CoursDto> mesCours;
        try {
            List<CoursDto> visibles = coursClient.catalogue().data();
            mesCours = (visibles == null ? List.<CoursDto>of() : visibles).stream()
                    .filter(c -> current.isAdmin() || current.id().equals(c.enseignantId()))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Cours du formateur indisponibles : {}", e.getMessage());
            return new ApprenantsResponse(List.of(), List.of(), source(Statut.INDISPONIBLE, "cours-service n'a pas répondu"),
                    "Tes cours sont indisponibles pour le moment.", null);
        }
        List<CoursResume> cours = mesCours.stream().map(c -> new CoursResume(c.id(), c.titre())).toList();
        if (cours.isEmpty()) {
            return new ApprenantsResponse(cours, List.of(), null, "Tu n'as pas encore de cours.", null);
        }

        // Inscrits de chaque cours (contrat 7). Route absente : on s'arrête au premier cours, inutile d'insister.
        Map<Long, String> titres = new LinkedHashMap<>();
        mesCours.forEach(c -> titres.put(c.id(), c.titre()));
        List<InscriptionDto> inscriptions = new ArrayList<>();
        for (CoursDto c : mesCours) {
            try {
                List<InscriptionDto> liste = coursClient.inscriptionsDuCours(c.id()).data();
                if (liste != null) {
                    liste.forEach(i -> inscriptions.add(i.coursId() == null ? copieAvecCours(i, c.id()) : i));
                }
            } catch (FeignException e) {
                boolean absente = ROUTE_ABSENTE.contains(e.status());
                return new ApprenantsResponse(cours, List.of(),
                        absente ? source(Statut.PAS_ENCORE_DISPONIBLE, "Route pas encore implémentée par cours-service")
                                : source(Statut.INDISPONIBLE, "cours-service n'a pas répondu"),
                        absente ? "La liste des inscrits arrivera avec les inscriptions aux cours (cours-service, contrat 7)."
                                : "La liste des inscrits est indisponible pour le moment.", null);
            } catch (RuntimeException e) {
                return new ApprenantsResponse(cours, List.of(), source(Statut.INDISPONIBLE, "cours-service n'a pas répondu"),
                        "La liste des inscrits est indisponible pour le moment.", null);
            }
        }

        // Apprenants concernés, leurs indicateurs, puis le risque en un seul appel pour ceux aux données complètes.
        Map<Long, User> apprenants = new LinkedHashMap<>();
        for (InscriptionDto i : inscriptions) {
            apprenants.computeIfAbsent(i.etudiantId(),
                    id -> userRepository.findById(id).filter(u -> u.getRole() == Role.ETUDIANT).orElse(null));
        }
        apprenants.values().removeIf(u -> u == null);

        List<Long> complets = new ArrayList<>();
        Map<Long, String> incomplets = new LinkedHashMap<>();
        List<IndicateursResponse.Indicateurs> vecteurs = new ArrayList<>();
        for (Long id : apprenants.keySet()) {
            IndicateursResponse ind = indicateursService.calculer(id, false);
            if (ind.complet()) {
                complets.add(id);
                vecteurs.add(ind.indicateurs());
            } else {
                incomplets.put(id, IaApprenantService.donneesIncompletes(ind.sources()));
            }
        }
        Map<Long, RisqueIaDto.Resultat> risques = new LinkedHashMap<>();
        Map<Long, String> profils = new LinkedHashMap<>();
        Boolean simulees = null;
        String iaIndisponible = null;
        if (!vecteurs.isEmpty()) {
            LotIndicateursIaDto lot = new LotIndicateursIaDto(vecteurs.stream()
                    .map(v -> IaApprenantService.lot(v).apprenants().get(0)).toList());
            try {
                RisqueIaDto r = aiClient.risque(authorization, lot).data();
                ProfilIaDto p = aiClient.profil(authorization, lot).data();
                for (int k = 0; k < complets.size(); k++) {
                    risques.put(complets.get(k), r.resultats().get(k));
                    profils.put(complets.get(k), p.groupes().get(k).nom());
                }
                simulees = r.donneesSimulees();
            } catch (RuntimeException e) {
                log.warn("IA du module User indisponible : {}", e.getMessage());
                iaIndisponible = "Le service d'IA est indisponible pour le moment.";
            }
        }

        List<Ligne> lignes = new ArrayList<>();
        for (InscriptionDto i : inscriptions) {
            User u = apprenants.get(i.etudiantId());
            if (u == null) {
                continue;
            }
            RisqueIaDto.Resultat risque = risques.get(u.getId());
            String raison = risque != null ? null
                    : iaIndisponible != null && complets.contains(u.getId()) ? iaIndisponible
                    : incomplets.getOrDefault(u.getId(), IaApprenantService.DONNEES_INCOMPLETES);
            lignes.add(new Ligne(u.getId(), u.getNom(), u.getPrenom(), u.getAvatarVersion(), i.coursId(),
                    titres.get(i.coursId()), i.dateInscription(), i.progression(), i.tempsPasseMinutes(),
                    i.dateDerniereActivite(), risque, raison, profils.get(u.getId())));
        }
        return new ApprenantsResponse(cours, lignes, source(Statut.DISPONIBLE, null), null, simulees);
    }

    private static Source source(Statut statut, String detail) {
        return new Source("cours-service", CONTRAT_7, statut, detail);
    }

    private static InscriptionDto copieAvecCours(InscriptionDto i, Long coursId) {
        return new InscriptionDto(i.etudiantId(), coursId, i.dateInscription(), i.progression(), i.tempsPasseMinutes(),
                i.termine(), i.dateDerniereActivite());
    }
}

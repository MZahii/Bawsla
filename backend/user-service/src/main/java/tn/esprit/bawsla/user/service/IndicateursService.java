package tn.esprit.bawsla.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import feign.FeignException;
import tn.esprit.bawsla.user.client.CoursClient;
import tn.esprit.bawsla.user.client.HistoriqueTentativeDto;
import tn.esprit.bawsla.user.client.InscriptionDto;
import tn.esprit.bawsla.user.client.NotionRateeDto;
import tn.esprit.bawsla.user.client.QuizClient;
import tn.esprit.bawsla.user.client.TentativeDto;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.IndicateursResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Indicateurs;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Source;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Statut;
import tn.esprit.bawsla.user.entity.Parcours;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

/**
 * Indicateurs chiffrés d'un apprenant pour le Machine Learning (contrat 5, module User), rassemblés depuis :
 * quiz-service (contrat 6 tentatives, contrat 3 notions ratées), cours-service (contrat 7 inscriptions) et
 * user-service (activité, parcours). Chaque source est appelée séparément : si l'une manque (route pas encore
 * implémentée, service arrêté), ses indicateurs restent null et son statut l'explique ; les autres sont gardés.
 */
@Service
public class IndicateursService {

    private static final Logger log = LoggerFactory.getLogger(IndicateursService.class);
    private static final Set<Integer> ROUTE_ABSENTE = Set.of(400, 404, 405);

    private final UserRepository userRepository;
    private final ActiviteService activiteService;
    private final QuizClient quizClient;
    private final CoursClient coursClient;
    private final Clock clock;

    public IndicateursService(UserRepository userRepository, ActiviteService activiteService, QuizClient quizClient,
                              CoursClient coursClient, Clock clock) {
        this.userRepository = userRepository;
        this.activiteService = activiteService;
        this.quizClient = quizClient;
        this.coursClient = coursClient;
        this.clock = clock;
    }

    /** Ses propres indicateurs (l'apprenant compte comme actif aujourd'hui). */
    public IndicateursResponse lesMiens(CurrentUser current) {
        activiteService.enregistrer(current.id());
        return calculer(current.id(), true);
    }

    /** Indicateurs d'un apprenant, pour un formateur ou un admin. */
    public IndicateursResponse dUnApprenant(CurrentUser current, Long etudiantId) {
        current.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        User u = getUser(etudiantId);
        if (u.getRole() != Role.ETUDIANT) {
            throw new ResourceNotFoundException("Apprenant", etudiantId);
        }
        return calculer(etudiantId, false);
    }

    /**
     * @param soiMeme true si l'apprenant consulte ses propres indicateurs : on peut alors se replier sur
     *                {@code /api/quiz/tentatives/mes} (contrat 11) si le contrat 6 échoue.
     */
    IndicateursResponse calculer(Long etudiantId, boolean soiMeme) {
        User user = getUser(etudiantId);
        Map<String, Source> sources = new LinkedHashMap<>();

        Resultat<List<TentativeDto>> tentatives = appeler(sources, "tentatives", "quiz-service", "contrat 6",
                () -> quizClient.tentatives(etudiantId));
        if (!tentatives.ok() && soiMeme) {
            // Repli : ses propres tentatives. Sans nbCorrectes ni nbQuestions, chaque tentative compte pour son
            // pourcentage sur 100 questions : le taux de bonnes réponses devient la moyenne des pourcentages.
            // Statut du repli noté à part : s'il échoue, on garde celui du contrat 6 (plus parlant).
            Map<String, Source> repli = new LinkedHashMap<>();
            Resultat<List<HistoriqueTentativeDto>> mes = appeler(repli, "tentatives", "quiz-service",
                    "GET /api/quiz/tentatives/mes", quizClient::mesTentatives);
            if (mes.ok()) {
                sources.putAll(repli);
                tentatives = new Resultat<>(mes.donnees().stream()
                        .map(h -> new TentativeDto(h.quizId(), h.coursId(), h.pourcentage(), 100, h.pourcentage(),
                                h.reussie(), h.dateTentative()))
                        .toList(), true);
            }
        }
        Resultat<List<NotionRateeDto>> notions = appeler(sources, "notionsRatees", "quiz-service", "contrat 3",
                () -> quizClient.notionsRatees(etudiantId));
        Resultat<List<InscriptionDto>> inscriptions = appeler(sources, "inscriptions", "cours-service", "contrat 7",
                () -> coursClient.inscriptions(etudiantId));
        sources.put("activite", new Source("user-service", "interne", Statut.DISPONIBLE,
                "Connexions et jours actifs enregistrés par user-service"));

        // Quiz : tentatives (note, bonnes réponses, nombre de quiz).
        Double noteMoyenne = null, tauxReussite = null;
        Integer nbQuiz = null;
        if (tentatives.ok()) {
            List<TentativeDto> t = tentatives.donnees();
            nbQuiz = t.size();
            noteMoyenne = t.isEmpty() ? 0.0 : arrondi(t.stream().mapToInt(TentativeDto::pourcentage).average().orElse(0) / 100);
            int questions = t.stream().mapToInt(TentativeDto::nbQuestions).sum();
            tauxReussite = questions == 0 ? 0.0 : arrondi((double) t.stream().mapToInt(TentativeDto::nbCorrectes).sum() / questions);
        }

        // Quiz : notions ratées.
        Integer nbNotions = null, nbErreurs = null;
        List<Long> coursEnDifficulte = List.of();
        if (notions.ok()) {
            List<NotionRateeDto> n = notions.donnees();
            nbNotions = n.size();
            nbErreurs = n.stream().mapToInt(NotionRateeDto::nbErreurs).sum();
            coursEnDifficulte = n.stream().map(NotionRateeDto::coursId).filter(c -> c != null).distinct().toList();
        }

        // Cours : inscriptions.
        Double progression = null;
        Integer temps = null, nbCours = null, nbTermines = null;
        List<Long> coursSuivis = List.of();
        if (inscriptions.ok()) {
            List<InscriptionDto> i = inscriptions.donnees();
            nbCours = i.size();
            progression = i.isEmpty() ? 0.0 : arrondi(i.stream().mapToDouble(InscriptionDto::progression).average().orElse(0));
            // Temps passé : connu seulement si cours-service le mesure pour TOUTES les inscriptions (lot 2) ;
            // sinon il reste inconnu (null), jamais remplacé par un faux 0.
            boolean mesure = i.stream().allMatch(x -> x.tempsPasseMinutes() != null);
            temps = mesure ? i.stream().mapToInt(InscriptionDto::tempsPasseMinutes).sum() : null;
            sources.put("tempsPasse", mesure
                    ? new Source("cours-service", "contrat 7", Statut.DISPONIBLE, null)
                    : new Source("cours-service", "contrat 7", Statut.PAS_ENCORE_DISPONIBLE,
                    "Le temps passé n'est pas encore mesuré par cours-service"));
            nbTermines = (int) i.stream().filter(InscriptionDto::termine).count();
            coursSuivis = i.stream().map(InscriptionDto::coursId).distinct().toList();
        }

        // user-service : activité.
        LocalDateTime reference = user.getDerniereConnexion() != null ? user.getDerniereConnexion() : user.getDateCreation();
        int joursSansConnexion = (int) Math.max(0, Duration.between(reference, LocalDateTime.now(clock)).toDays());

        Parcours parcours = user.getParcours();
        List<String> domaines = parcours == null ? List.of() : parcours.getDomaines();

        Indicateurs indicateurs = new Indicateurs(noteMoyenne, tauxReussite, nbQuiz, nbNotions, nbErreurs, progression,
                temps, nbCours, nbTermines, activiteService.joursActifs(etudiantId), joursSansConnexion);
        boolean complet = tentatives.ok() && notions.ok() && inscriptions.ok() && temps != null;
        return new IndicateursResponse(indicateurs, coursSuivis, coursEnDifficulte, domaines, sources, complet);
    }

    private record Resultat<T>(T donnees, boolean ok) {
    }

    /** Appelle une source et note son statut, sans jamais faire échouer l'ensemble. */
    private <T> Resultat<T> appeler(Map<String, Source> sources, String nom, String fournisseur, String contrat,
                                    Supplier<ApiResponse<T>> appel) {
        try {
            ApiResponse<T> reponse = appel.get();
            if ("stub".equalsIgnoreCase(reponse.message())) {
                sources.put(nom, new Source(fournisseur, contrat, Statut.STUB, "La route renvoie encore un bouchon"));
                return new Resultat<>(null, false);
            }
            sources.put(nom, new Source(fournisseur, contrat, Statut.DISPONIBLE, null));
            return new Resultat<>(reponse.data(), reponse.data() != null);
        } catch (FeignException e) {
            if (ROUTE_ABSENTE.contains(e.status())) {
                // Requêtes toujours bien formées : 404, 405, ou 400 quand le chemin tombe sur /{id} (« tentatives »
                // n'est pas un nombre), veulent dire que la route du contrat n'existe pas encore.
                sources.put(nom, new Source(fournisseur, contrat, Statut.PAS_ENCORE_DISPONIBLE,
                        "Route pas encore implémentée par " + fournisseur));
                return new Resultat<>(null, false);
            }
            log.warn("Source {} ({}) indisponible : {}", nom, fournisseur, e.getMessage());
            sources.put(nom, new Source(fournisseur, contrat, Statut.INDISPONIBLE, fournisseur + " n'a pas répondu"));
        } catch (RuntimeException e) {
            log.warn("Source {} ({}) indisponible : {}", nom, fournisseur, e.getMessage());
            sources.put(nom, new Source(fournisseur, contrat, Statut.INDISPONIBLE, fournisseur + " n'a pas répondu"));
        }
        return new Resultat<>(null, false);
    }

    private static double arrondi(double x) {
        return Math.round(x * 100) / 100.0;
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }
}

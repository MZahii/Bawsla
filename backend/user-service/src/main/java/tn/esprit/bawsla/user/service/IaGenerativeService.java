package tn.esprit.bawsla.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tn.esprit.bawsla.user.client.AiClient;
import tn.esprit.bawsla.user.client.CoursClient;
import tn.esprit.bawsla.user.client.CoursDto;
import tn.esprit.bawsla.user.client.IaGenerativeDto;
import tn.esprit.bawsla.user.client.IaGenerativeDto.CoursEnCours;
import tn.esprit.bawsla.user.client.IaGenerativeDto.CoursStat;
import tn.esprit.bawsla.user.client.IaGenerativeDto.Effectif;
import tn.esprit.bawsla.user.client.IaGenerativeDto.RepartitionRisque;
import tn.esprit.bawsla.user.client.InscriptionDto;
import tn.esprit.bawsla.user.client.NotionRateeDto;
import tn.esprit.bawsla.user.client.QuizClient;
import tn.esprit.bawsla.user.client.RecommandationsIaDto;
import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.user.common.exception.UnauthorizedException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.ApprenantsResponse;
import tn.esprit.bawsla.user.dto.ApprenantsResponse.Ligne;
import tn.esprit.bawsla.user.dto.ContenuIaResponse;
import tn.esprit.bawsla.user.dto.IaApprenantResponse;
import tn.esprit.bawsla.user.entity.ContenuIa;
import tn.esprit.bawsla.user.entity.Parcours;
import tn.esprit.bawsla.user.entity.TypeContenuIa;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.ContenuIaRepository;
import tn.esprit.bawsla.user.repository.UserRepository;

/**
 * IA générative du module User : le Machine Learning décide (profil, risque, recommandations), le LLM d'ai-service
 * l'explique (conseil, plan de la semaine, explication du risque, rapport de la classe, domaines depuis une phrase).
 *
 * <p>Vie privée : seuls des nombres, des libellés, des notions et des titres de cours partent vers l'IA ; jamais le
 * nom, l'email ni l'identifiant d'un utilisateur. Quota : chaque texte est gardé ({@link ContenuIa}) et réutilisé
 * tant que les données envoyées n'ont pas changé. Si le LLM est indisponible, une erreur 503 claire est renvoyée :
 * les résultats du Machine Learning, eux, restent affichés par leurs propres routes.</p>
 */
@Service
public class IaGenerativeService {

    private static final Logger log = LoggerFactory.getLogger(IaGenerativeService.class);
    static final String INDISPONIBLE = "L'IA générative est indisponible pour le moment (quota atteint ou fournisseur "
            + "non configuré). Réessaie dans quelques minutes : les résultats du Machine Learning restent affichés.";
    static final String SANS_INSCRIT = "Pas encore d'inscrits à tes cours : le rapport arrivera avec eux.";
    static final String SANS_COURS = "Inscris-toi à un cours pour recevoir ton plan de la semaine.";
    static final long SANS_CIBLE = 0L;
    private static final int MAX_NOTIONS = 5;
    private static final int MAX_LIGNES_RAPPORT = 10;

    private final IaApprenantService iaApprenantService;
    private final ApprenantsService apprenantsService;
    private final QuizClient quizClient;
    private final CoursClient coursClient;
    private final AiClient aiClient;
    private final UserRepository userRepository;
    private final ContenuIaRepository contenuRepository;
    private final ObjectMapper mapper;

    public IaGenerativeService(IaApprenantService iaApprenantService, ApprenantsService apprenantsService,
                               QuizClient quizClient, CoursClient coursClient, AiClient aiClient,
                               UserRepository userRepository, ContenuIaRepository contenuRepository,
                               ObjectMapper mapper) {
        this.iaApprenantService = iaApprenantService;
        this.apprenantsService = apprenantsService;
        this.quizClient = quizClient;
        this.coursClient = coursClient;
        this.aiClient = aiClient;
        this.userRepository = userRepository;
        this.contenuRepository = contenuRepository;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ lecture du dernier texte (sans appel au LLM)

    /** Dernier texte généré de ce type ; {@code null} s'il n'y en a pas encore. */
    public ContenuIaResponse dernier(CurrentUser current, TypeContenuIa type, Long cible) {
        switch (type) {
            case CONSEIL, PLAN -> current.requireAnyRole(CurrentUser.ETUDIANT);
            default -> current.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        }
        return contenuRepository.findByUserIdAndTypeAndCible(current.id(), type, cible == null ? SANS_CIBLE : cible)
                .map(c -> reponse(c, true))
                .orElse(null);
    }

    // ------------------------------------------------------------------ 1. conseil (apprenant)

    public ContenuIaResponse conseil(CurrentUser current, String authorization) {
        current.requireAnyRole(CurrentUser.ETUDIANT);
        exigerJeton(authorization);
        IaApprenantResponse ia = iaApprenantService.pourMoi(current, authorization);
        if (!ia.complet() || ia.indicateurs() == null) {
            throw new BadRequestException(ia.profilIndisponible() != null ? ia.profilIndisponible()
                    : IaApprenantService.DONNEES_INCOMPLETES);
        }
        Parcours parcours = parcours(current.id());
        Map<Long, String> titres = titresDuCatalogue();
        List<String> suivis = inscriptions(current.id()).stream()
                .map(i -> titres.get(i.coursId())).filter(Objects::nonNull).limit(10).toList();
        IaGenerativeDto.Conseil requete = new IaGenerativeDto.Conseil(
                IaApprenantService.lot(ia.indicateurs()).apprenants().get(0), nomProfil(ia), parcours.getObjectif(),
                suivis, notions(current.id()), titresRecommandes(ia));
        return generer(current.id(), TypeContenuIa.CONSEIL, SANS_CIBLE, requete,
                () -> aiClient.conseil(authorization, requete).data());
    }

    // ------------------------------------------------------------------ 2. explication du risque (formateur, admin)

    public ContenuIaResponse explication(CurrentUser current, Long etudiantId, String authorization) {
        current.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        exigerJeton(authorization);
        IaApprenantResponse ia = iaApprenantService.pourUnApprenant(current, etudiantId, authorization);
        if (ia.risque() == null || ia.indicateurs() == null) {
            throw new BadRequestException(ia.risqueIndisponible() != null ? ia.risqueIndisponible()
                    : IaApprenantService.DONNEES_INCOMPLETES);
        }
        IaGenerativeDto.ExplicationRisque requete = new IaGenerativeDto.ExplicationRisque(
                ia.risque().niveau(), ia.risque().probabilite(), ia.risque().facteurs(),
                IaApprenantService.lot(ia.indicateurs()).apprenants().get(0), nomProfil(ia), notions(etudiantId));
        return generer(current.id(), TypeContenuIa.EXPLICATION_RISQUE, etudiantId, requete,
                () -> aiClient.explicationRisque(authorization, requete).data());
    }

    // ------------------------------------------------------------------ 3. plan de la semaine (apprenant)

    public ContenuIaResponse plan(CurrentUser current, String authorization) {
        current.requireAnyRole(CurrentUser.ETUDIANT);
        exigerJeton(authorization);
        IaApprenantResponse ia = iaApprenantService.pourMoi(current, authorization);
        Parcours parcours = parcours(current.id());
        Map<Long, String> titres = titresDuCatalogue();
        List<CoursEnCours> enCours = inscriptions(current.id()).stream()
                .filter(i -> !i.termine() && titres.containsKey(i.coursId()))
                .limit(5)
                .map(i -> new CoursEnCours(titres.get(i.coursId()), i.progression()))
                .toList();
        List<String> notions = notions(current.id());
        List<String> recommandes = titresRecommandes(ia);
        if (enCours.isEmpty() && notions.isEmpty() && recommandes.isEmpty()) {
            throw new BadRequestException(SANS_COURS);
        }
        IaGenerativeDto.Plan requete = new IaGenerativeDto.Plan(parcours.getTemps(), parcours.getObjectif(),
                nomProfil(ia), enCours, notions, recommandes);
        return generer(current.id(), TypeContenuIa.PLAN, SANS_CIBLE, requete,
                () -> aiClient.plan(authorization, requete).data());
    }

    // ------------------------------------------------------------------ 4. rapport de la classe (formateur, admin)

    /** Statistiques AGRÉGÉES des inscrits aux cours du formateur (tous les cours pour l'admin) : aucun apprenant nommé. */
    public ContenuIaResponse rapport(CurrentUser current, String authorization) {
        current.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        exigerJeton(authorization);
        ApprenantsResponse a = apprenantsService.pourFormateur(current, authorization);
        if (a.lignes().isEmpty()) {
            throw new BadRequestException(a.message() != null ? a.message() : SANS_INSCRIT);
        }
        IaGenerativeDto.Cohorte requete = cohorte(a);
        return generer(current.id(), TypeContenuIa.RAPPORT_COHORTE, SANS_CIBLE, requete,
                () -> aiClient.rapportCohorte(authorization, requete).data());
    }

    IaGenerativeDto.Cohorte cohorte(ApprenantsResponse a) {
        Map<Long, Ligne> parApprenant = new LinkedHashMap<>();
        a.lignes().forEach(l -> parApprenant.putIfAbsent(l.etudiantId(), l));

        int faible = 0, moyen = 0, eleve = 0, inconnu = 0;
        Map<String, Integer> profils = new LinkedHashMap<>();
        Map<String, Integer> notions = new LinkedHashMap<>();
        for (Ligne l : parApprenant.values()) {
            String niveau = l.risque() == null ? "" : l.risque().niveau();
            switch (niveau) {
                case "faible" -> faible++;
                case "moyen" -> moyen++;
                case "eleve" -> eleve++;
                default -> inconnu++;
            }
            if (l.profil() != null) {
                profils.merge(l.profil(), 1, Integer::sum);
            }
            // Nombre d'apprenants concernés par chaque notion (une fois par apprenant).
            new LinkedHashSet<>(notions(l.etudiantId())).forEach(n -> notions.merge(n, 1, Integer::sum));
        }

        Map<Long, List<Ligne>> parCours = a.lignes().stream()
                .collect(Collectors.groupingBy(Ligne::coursId, LinkedHashMap::new, Collectors.toList()));
        List<CoursStat> cours = parCours.values().stream()
                .map(ls -> new CoursStat(ls.get(0).coursTitre(), ls.size(), moyenne(ls)))
                .sorted(Comparator.comparingInt(CoursStat::nbInscrits).reversed())
                .limit(MAX_LIGNES_RAPPORT)
                .toList();
        return new IaGenerativeDto.Cohorte(parApprenant.size(), a.cours().size(),
                new RepartitionRisque(faible, moyen, eleve, inconnu), moyenne(a.lignes()), null,
                effectifs(profils), effectifs(notions), cours);
    }

    // ------------------------------------------------------------------ 5. domaines depuis une phrase (apprenant)

    /** Suggestion de domaines et d'objectif à partir d'une phrase libre ; l'apprenant choisit ensuite d'enregistrer. */
    public JsonNode suggestionParcours(CurrentUser current, String texte, String authorization) {
        current.requireAnyRole(CurrentUser.ETUDIANT);
        exigerJeton(authorization);
        IaGenerativeDto.Domaines requete = new IaGenerativeDto.Domaines(texte, ListesParcours.DOMAINES,
                ListesParcours.OBJECTIFS);
        try {
            JsonNode r = aiClient.domaines(authorization, requete).data();
            if (r == null) {
                throw new ServiceUnavailableException(INDISPONIBLE);
            }
            return r;
        } catch (RuntimeException e) {
            log.warn("IA générative indisponible (domaines) : {}", e.getMessage());
            throw new ServiceUnavailableException(INDISPONIBLE);
        }
    }

    // ------------------------------------------------------------------ génération avec cache

    /**
     * Réutilise le dernier texte si la requête est identique (même empreinte SHA-256) ; sinon appelle l'IA et
     * remplace le texte gardé.
     */
    ContenuIaResponse generer(Long userId, TypeContenuIa type, Long cible, Object requete, Supplier<JsonNode> appel) {
        String empreinte = empreinte(requete);
        ContenuIa contenu = contenuRepository.findByUserIdAndTypeAndCible(userId, type, cible).orElse(null);
        if (contenu != null && empreinte.equals(contenu.getEmpreinte())) {
            return reponse(contenu, true);
        }
        JsonNode genere;
        try {
            genere = appel.get();
        } catch (RuntimeException e) {
            log.warn("IA générative indisponible ({}) : {}", type, e.getMessage());
            throw new ServiceUnavailableException(INDISPONIBLE);
        }
        if (genere == null) {
            throw new ServiceUnavailableException(INDISPONIBLE);
        }
        if (contenu == null) {
            contenu = new ContenuIa(userId, type, cible);
        }
        String fournisseur = genere.path("fournisseur").isTextual() ? genere.path("fournisseur").asText() : null;
        contenu.remplacer(empreinte, genere.toString(), fournisseur, LocalDateTime.now());
        try {
            contenuRepository.save(contenu);
        } catch (DataIntegrityViolationException e) {
            // Double clic (une autre requête vient d'enregistrer ce texte) ou texte refusé par la base : l'apprenant
            // reçoit quand même le texte, il sera simplement régénéré au prochain clic.
            log.warn("Texte IA ({}) non gardé : {}", type, e.getMostSpecificCause().getMessage());
        }
        return new ContenuIaResponse(type.name(), genere, fournisseur, avecFuseau(contenu.getDateGeneration()), false);
    }

    private ContenuIaResponse reponse(ContenuIa c, boolean depuisCache) {
        try {
            return new ContenuIaResponse(c.getType().name(), mapper.readTree(c.getContenu()), c.getFournisseur(),
                    avecFuseau(c.getDateGeneration()), depuisCache);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Contenu IA illisible", e);
        }
    }

    /** Date enregistrée à l'heure du serveur (UTC en Docker) → avec son décalage, pour l'afficher à l'heure locale. */
    private static OffsetDateTime avecFuseau(LocalDateTime date) {
        return date == null ? null : date.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    String empreinte(Object requete) {
        try {
            byte[] octets = mapper.writeValueAsString(requete).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Empreinte impossible", e);
        }
    }

    // ------------------------------------------------------------------ données d'entrée

    /** Noms des notions ratées (contrat 3), de la plus ratée à la moins ratée ; liste vide si quiz-service ne répond pas. */
    private List<String> notions(Long etudiantId) {
        try {
            List<NotionRateeDto> n = quizClient.notionsRatees(etudiantId).data();
            return n == null ? List.of() : n.stream().map(NotionRateeDto::notion).filter(Objects::nonNull)
                    .distinct().limit(MAX_NOTIONS).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<InscriptionDto> inscriptions(Long etudiantId) {
        try {
            List<InscriptionDto> i = coursClient.inscriptions(etudiantId).data();
            return i == null ? List.of() : i;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private Map<Long, String> titresDuCatalogue() {
        try {
            List<CoursDto> cours = coursClient.catalogue().data();
            Map<Long, String> titres = new LinkedHashMap<>();
            (cours == null ? List.<CoursDto>of() : cours).forEach(c -> titres.put(c.id(), c.titre()));
            return titres;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private Parcours parcours(Long userId) {
        Parcours p = userRepository.findById(userId).map(User::getParcours).orElse(null);
        return p == null ? new Parcours() : p;
    }

    private static String nomProfil(IaApprenantResponse ia) {
        return ia.profil() == null ? null : ia.profil().nom();
    }

    private static List<String> titresRecommandes(IaApprenantResponse ia) {
        return ia.recommandations() == null ? List.of()
                : ia.recommandations().stream().map(RecommandationsIaDto.CoursRecommande::titre).limit(3).toList();
    }

    private static Double moyenne(List<Ligne> lignes) {
        var p = lignes.stream().map(Ligne::progression).filter(Objects::nonNull).mapToDouble(Double::doubleValue);
        var stats = p.summaryStatistics();
        return stats.getCount() == 0 ? null : Math.round(stats.getAverage() * 100) / 100.0;
    }

    private static List<Effectif> effectifs(Map<String, Integer> comptes) {
        List<Effectif> liste = new ArrayList<>();
        comptes.forEach((nom, n) -> liste.add(new Effectif(nom, n)));
        liste.sort(Comparator.comparingInt(Effectif::nombre).reversed());
        return liste.subList(0, Math.min(MAX_LIGNES_RAPPORT, liste.size()));
    }

    private static void exigerJeton(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Jeton d'authentification manquant");
        }
    }
}

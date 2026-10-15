package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.entity.ParametreForum;
import tn.esprit.bawsla.forum.entity.ParametresForumCours;
import tn.esprit.bawsla.forum.repository.ParametreForumRepository;
import tn.esprit.bawsla.forum.repository.ParametresForumCoursRepository;

/**
 * Réglages du forum : globaux (clé / valeur, réglés par l'admin, valeurs par défaut ci-dessous) et par cours
 * (réglés par le formateur du cours). Un cours sans ligne en base a les réglages par défaut.
 */
@Service
@Transactional(readOnly = true)
public class ParametresService {

    public static final String SEUIL_MASQUAGE = "moderation.seuil-masquage";
    public static final String SEUIL_SIGNALEMENT = "moderation.seuil-signalement";
    public static final String MOTS_INTERDITS = "moderation.mots-interdits";
    public static final String QUESTIONS_PAR_HEURE = "flood.questions-par-heure";
    public static final String MESSAGES_PAR_HEURE = "flood.messages-par-heure";
    public static final String POINTS_VOTE_NEGATIF = "vote.points-minimum-negatif";
    public static final String SEUIL_SIMILAIRE = "similarite.seuil";
    public static final String SEUIL_DOUBLON = "similarite.seuil-doublon";
    public static final String REPONSE_GENERALE = "vigie.reponse-generale-active";

    /** Valeurs par défaut (plan §3.3, §3.7, §4.5) ; les seuils de similarité sont fixés par l'évaluation (§9). */
    private static final Map<String, String> DEFAUTS = new LinkedHashMap<>();

    static {
        DEFAUTS.put(SEUIL_MASQUAGE, "0.85");
        DEFAUTS.put(SEUIL_SIGNALEMENT, "0.5");
        DEFAUTS.put(MOTS_INTERDITS, "");
        DEFAUTS.put(QUESTIONS_PAR_HEURE, "5");
        DEFAUTS.put(MESSAGES_PAR_HEURE, "30");
        DEFAUTS.put(POINTS_VOTE_NEGATIF, "50");
        DEFAUTS.put(SEUIL_SIMILAIRE, "0.75");
        DEFAUTS.put(SEUIL_DOUBLON, "0.9");
        DEFAUTS.put(REPONSE_GENERALE, "true");
    }

    private final ParametreForumRepository parametreRepository;
    private final ParametresForumCoursRepository coursRepository;
    private final Clock horloge;

    public ParametresService(ParametreForumRepository parametreRepository,
                             ParametresForumCoursRepository coursRepository, Clock horloge) {
        this.parametreRepository = parametreRepository;
        this.coursRepository = coursRepository;
        this.horloge = horloge;
    }

    public String texte(String cle) {
        return parametreRepository.findById(cle).map(ParametreForum::getValeur).orElse(DEFAUTS.get(cle));
    }

    public int entier(String cle) {
        return Integer.parseInt(texte(cle).trim());
    }

    public double decimal(String cle) {
        return Double.parseDouble(texte(cle).trim());
    }

    public boolean booleen(String cle) {
        return Boolean.parseBoolean(texte(cle).trim());
    }

    /** Tous les réglages globaux (valeurs en base, sinon par défaut), dans un ordre stable. */
    public Map<String, String> tous() {
        Map<String, String> valeurs = new LinkedHashMap<>(DEFAUTS);
        parametreRepository.findAll().forEach(p -> valeurs.put(p.getCle(), p.getValeur()));
        return valeurs;
    }

    /** Met à jour des réglages globaux ; une clé inconnue ou une valeur mal formée est refusée (400). */
    @Transactional
    public Map<String, String> modifier(Map<String, String> valeurs, Long adminId) {
        valeurs.forEach((cle, valeur) -> {
            if (!DEFAUTS.containsKey(cle)) {
                throw new BadRequestException("Réglage inconnu : " + cle);
            }
            valider(cle, valeur == null ? "" : valeur.trim());
        });
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        valeurs.forEach((cle, valeur) -> {
            ParametreForum p = parametreRepository.findById(cle).orElseGet(ParametreForum::new);
            p.setCle(cle);
            p.setValeur(valeur == null ? "" : valeur.trim());
            p.setModifieParId(adminId);
            p.setModifieLe(maintenant);
            parametreRepository.save(p);
        });
        return tous();
    }

    /** Réglages du forum d'un cours (non enregistrés tant que le formateur ne les a pas modifiés). */
    public ParametresForumCours cours(Long coursId) {
        return coursRepository.findById(coursId).orElseGet(() -> {
            ParametresForumCours p = new ParametresForumCours();
            p.setCoursId(coursId);
            return p;
        });
    }

    /** Mode examen actif : activé et pas encore arrivé à sa date de fin (sans date = jusqu'à désactivation). */
    public boolean modeExamenActif(ParametresForumCours p) {
        return p.isModeExamen()
                && (p.getModeExamenFin() == null || p.getModeExamenFin().isAfter(LocalDateTime.now(horloge)));
    }

    private static void valider(String cle, String valeur) {
        try {
            switch (cle) {
                case SEUIL_MASQUAGE, SEUIL_SIGNALEMENT, SEUIL_SIMILAIRE, SEUIL_DOUBLON -> {
                    double v = Double.parseDouble(valeur);
                    if (v < 0 || v > 1) {
                        throw new BadRequestException("Le réglage " + cle + " est compris entre 0 et 1");
                    }
                }
                case QUESTIONS_PAR_HEURE, MESSAGES_PAR_HEURE, POINTS_VOTE_NEGATIF -> {
                    if (Integer.parseInt(valeur) < 0) {
                        throw new BadRequestException("Le réglage " + cle + " est positif");
                    }
                }
                case REPONSE_GENERALE -> {
                    if (!valeur.equals("true") && !valeur.equals("false")) {
                        throw new BadRequestException("Le réglage " + cle + " vaut true ou false");
                    }
                }
                default -> {
                    // texte libre (mots interdits)
                }
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("Valeur invalide pour " + cle);
        }
    }
}

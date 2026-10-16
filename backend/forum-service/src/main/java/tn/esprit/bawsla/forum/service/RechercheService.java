package tn.esprit.bawsla.forum.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.client.NotionRateeDto;
import tn.esprit.bawsla.forum.client.QuizClient;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.config.MigrationFulltext;
import tn.esprit.bawsla.forum.dto.DiscussionResume;
import tn.esprit.bawsla.forum.dto.PourToiResponse;
import tn.esprit.bawsla.forum.dto.SimilaireResponse;
import tn.esprit.bawsla.forum.dto.SimilairesRequest;
import tn.esprit.bawsla.forum.dto.SimilairesResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.service.FilService.Fil;
import tn.esprit.bawsla.forum.service.IaService.Vecteur;
import tn.esprit.bawsla.forum.service.Similarite.Score;

/**
 * Recherche dans le forum (plan §4.1) : la base vectorielle est la table {@code discussion} elle-même
 * (vecteurs normalisés en BLOB), la similarité cosinus est calculée ici, en Java.
 * <ul>
 *   <li>Questions similaires en direct : question reformulée (derja, arabizi, anglais → français technique),
 *       vectorisée, comparée aux discussions du cours ; au-dessus du seuil réglé par l'admin (§9).</li>
 *   <li>Recherche hybride : lexical (FULLTEXT MySQL, sinon score de mots) + sémantique, fusionnés par RRF (k = 60).</li>
 *   <li>Sans vectorisation (IA indisponible), tout passe en mots-clés et la réponse l'indique (semantique = false).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class RechercheService {

    static final int CANDIDATS = 20;
    static final int MAX_SIMILAIRES = 5;
    static final int MAX_LIEES = 5;
    static final int NOTIONS_POUR_TOI = 3;
    static final int FILS_PAR_NOTION = 2;

    /** Résultat d'une recherche : identifiants classés et leur score ; semantique = la partie vectorielle a servi. */
    public record Resultat(List<Score> classement, boolean semantique) {

        public List<Long> ids() {
            return classement.stream().map(Score::id).toList();
        }

        public Map<Long, Double> scores() {
            return classement.stream().collect(Collectors.toMap(Score::id, Score::score, (a, b) -> a,
                    LinkedHashMap::new));
        }
    }

    private final DiscussionRepository discussionRepository;
    private final IaService ia;
    private final AccesService acces;
    private final FilService fils;
    private final ParametresService parametres;
    private final PresentationService presentation;
    private final QuizClient quizClient;
    private final JdbcTemplate jdbc;
    private final boolean mysql;

    public RechercheService(DiscussionRepository discussionRepository, IaService ia, AccesService acces,
                            FilService fils, ParametresService parametres, PresentationService presentation,
                            QuizClient quizClient, JdbcTemplate jdbc, DataSource dataSource) {
        this.discussionRepository = discussionRepository;
        this.ia = ia;
        this.acces = acces;
        this.fils = fils;
        this.parametres = parametres;
        this.presentation = presentation;
        this.quizClient = quizClient;
        this.jdbc = jdbc;
        this.mysql = MigrationFulltext.estMysql(dataSource);
    }

    // ------------------------------------------------------------------ Questions similaires en direct

    public SimilairesResponse similaires(SimilairesRequest req, CurrentUser user) {
        acces.contexte(user, req.coursId());
        return similaires(req.coursId(), req.texte(), req.exclureId());
    }

    /** Similaires d'un texte dans un cours ; doublonId = discussion RÉSOLUE au-dessus du seuil « doublon ». */
    public SimilairesResponse similaires(Long coursId, String texte, Long exclureId) {
        double seuil = parametres.decimal(ParametresService.SEUIL_SIMILAIRE);
        double seuilDoublon = parametres.decimal(ParametresService.SEUIL_DOUBLON);
        List<Discussion> candidats = candidats(coursId, exclureId);
        Map<Long, Discussion> parId = candidats.stream().collect(Collectors.toMap(Discussion::getId, d -> d));
        Optional<Vecteur> vecteur = ia.vectoriserRequete(ia.reformuler(texte, List.of()));

        List<Score> classement;
        List<Score> semantiques = List.of();
        if (vecteur.isPresent()) {
            semantiques = semantique(candidats, vecteur.get()).stream().filter(s -> s.score() >= seuil).toList();
            // Discussion sans vecteur de ce modèle (vectorisation en échec à la publication : quota, panne) : repli
            // lexical pour elle seule, sinon elle resterait invisible. Jamais « doublon » : le score n'est pas un cosinus.
            List<Discussion> sansVecteur = candidats.stream().filter(d -> d.getEmbedding() == null
                    || !vecteur.get().modele().equals(d.getModeleEmbedding())).toList();
            classement = new ArrayList<>(semantiques);
            classement.addAll(lexicalJava(sansVecteur, texte).stream().filter(s -> s.score() >= 0.5).toList());
        } else {
            // Repli lexical : le score de mots n'est pas un cosinus, on garde un seuil fixe et prudent.
            classement = lexicalJava(candidats, texte).stream().filter(s -> s.score() >= 0.5).toList();
        }
        List<SimilaireResponse> resultats = classement.stream().limit(MAX_SIMILAIRES)
                .map(s -> similaire(parId.get(s.id()), s.score(), seuilDoublon)).toList();
        Long doublon = vecteur.isEmpty() ? null : semantiques.stream()
                .filter(s -> s.score() >= seuilDoublon && parId.get(s.id()).getStatut() == StatutDiscussion.RESOLUE)
                .map(Score::id).findFirst().orElse(null);
        return new SimilairesResponse(resultats, vecteur.isPresent(), doublon);
    }

    // ------------------------------------------------------------------ Recherche de la liste

    /** Recherche hybride (mode « semantique », par défaut) ou lexicale seule (mode « lexical », mots exacts). */
    public Resultat rechercher(Long coursId, String q, String mode) {
        List<Discussion> candidats = candidats(coursId, null);
        List<Score> lexical = lexical(coursId, q, candidats);
        if ("lexical".equals(mode)) {
            return new Resultat(lexical, false);
        }
        Optional<Vecteur> vecteur = ia.vectoriserRequete(ia.reformuler(q, List.of()));
        if (vecteur.isEmpty()) {
            return new Resultat(lexical, false);
        }
        double seuil = parametres.decimal(ParametresService.SEUIL_SIMILAIRE);
        // Le classement sémantique garde un plancher bas : la RRF rapproche ensuite les deux points de vue.
        List<Score> semantique = semantique(candidats, vecteur.get()).stream()
                .filter(s -> s.score() >= seuil * 0.6).limit(CANDIDATS).toList();
        return new Resultat(Similarite.rrf(List.of(lexical, semantique)), true);
    }

    // ------------------------------------------------------------------ Discussions liées

    /** Les 5 discussions du même cours les plus proches (vecteur déjà stocké : aucun appel à l'IA). */
    public List<SimilaireResponse> liees(Long discussionId, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        Discussion d = fil.discussion();
        List<Discussion> candidats = candidats(d.getCoursId(), d.getId());
        Map<Long, Discussion> parId = candidats.stream().collect(Collectors.toMap(Discussion::getId, x -> x));
        double seuilDoublon = parametres.decimal(ParametresService.SEUIL_DOUBLON);
        List<Score> classement = d.getEmbedding() != null
                ? semantique(candidats, new Vecteur(d.getEmbedding(), d.getModeleEmbedding()))
                : lexicalJava(candidats, d.getTitre());
        double plancher = d.getEmbedding() != null ? parametres.decimal(ParametresService.SEUIL_SIMILAIRE) * 0.8 : 0.3;
        return classement.stream().filter(s -> s.score() >= plancher).limit(MAX_LIEES)
                .map(s -> similaire(parId.get(s.id()), s.score(), seuilDoublon)).toList();
    }

    // ------------------------------------------------------------------ Pour toi

    /** Fils résolus liés aux notions ratées de l'apprenant (contrat 3) ; disponible = false si quiz-service se tait. */
    public PourToiResponse pourMoi(CurrentUser user) {
        acces.exigerConnecte(user);
        if (!CurrentUser.ETUDIANT.equals(user.role())) {
            return new PourToiResponse(false, List.of());
        }
        List<NotionRateeDto> notions;
        try {
            ApiResponse<List<NotionRateeDto>> r = quizClient.notionsRatees(user.id());
            notions = r == null || r.data() == null ? null : r.data();
        } catch (RuntimeException e) {
            notions = null;
        }
        if (notions == null) {
            return new PourToiResponse(false, List.of());
        }
        List<PourToiResponse.Notion> resultat = new ArrayList<>();
        for (NotionRateeDto notion : notions.stream().limit(NOTIONS_POUR_TOI).toList()) {
            if (notion.coursId() == null || notion.notion() == null || notion.notion().isBlank()) {
                continue;
            }
            List<Long> ids = rechercher(notion.coursId(), notion.notion(), "semantique").ids();
            List<Discussion> resolues = discussionRepository.findAllById(ids).stream()
                    .filter(d -> d.getStatut() == StatutDiscussion.RESOLUE)
                    .sorted(Comparator.comparingInt(d -> ids.indexOf(d.getId())))
                    .limit(FILS_PAR_NOTION).toList();
            if (!resolues.isEmpty()) {
                List<DiscussionResume> fils = presentation.resumes(resolues, user);
                resultat.add(new PourToiResponse.Notion(notion.notion(), notion.coursId(), fils));
            }
        }
        return new PourToiResponse(true, resultat);
    }

    // ------------------------------------------------------------------ Calculs

    private List<Discussion> candidats(Long coursId, Long exclureId) {
        return discussionRepository.candidats(coursId).stream()
                .filter(d -> exclureId == null || !d.getId().equals(exclureId)).toList();
    }

    /** Cosinus avec les discussions vectorisées par le MÊME modèle, décroissant. */
    static List<Score> semantique(List<Discussion> candidats, Vecteur vecteur) {
        return candidats.stream()
                .filter(d -> d.getEmbedding() != null && vecteur.modele().equals(d.getModeleEmbedding()))
                .map(d -> new Score(d.getId(), Similarite.cosinus(vecteur.valeurs(), d.getEmbedding())))
                .sorted(Comparator.comparingDouble(Score::score).reversed()).toList();
    }

    /** Lexical : FULLTEXT en mode naturel sur MySQL, score de mots en Java sinon (tests H2). */
    private List<Score> lexical(Long coursId, String q, List<Discussion> candidats) {
        if (!mysql) {
            return lexicalJava(candidats, q).stream().limit(CANDIDATS).toList();
        }
        Set<Long> visibles = candidats.stream().map(Discussion::getId).collect(Collectors.toSet());
        return jdbc.query("""
                        select id, match(titre, contenu) against (? in natural language mode) as score
                        from discussion where match(titre, contenu) against (? in natural language mode) > 0
                          and (? is null or cours_id = ?)
                        order by score desc limit ?""",
                        (rs, i) -> new Score(rs.getLong("id"), rs.getDouble("score")), q, q, coursId, coursId,
                        CANDIDATS * 2)
                .stream().filter(s -> visibles.contains(s.id())).limit(CANDIDATS).toList();
    }

    static List<Score> lexicalJava(List<Discussion> candidats, String q) {
        Set<String> mots = Similarite.mots(q);
        return candidats.stream()
                .map(d -> new Score(d.getId(), Similarite.lexical(mots, d.getTitre(), d.getContenu())))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingDouble(Score::score).reversed()).toList();
    }

    private static SimilaireResponse similaire(Discussion d, double score, double seuilDoublon) {
        double arrondi = Math.round(score * 1000) / 1000.0;
        return new SimilaireResponse(d.getId(), d.getTitre(), Textes.extrait(d.getContenu(), 160), d.getStatut(),
                d.getNbReponses(), arrondi, score >= seuilDoublon ? "TRES_PROCHE" : "PROCHE");
    }
}

package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.ApprenantBloque;
import tn.esprit.bawsla.forum.dto.DiscussionDetail;
import tn.esprit.bawsla.forum.dto.SerieStat;
import tn.esprit.bawsla.forum.dto.TendancesResponse;
import tn.esprit.bawsla.forum.dto.ThemeTendance;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.repository.ConversationVigieRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseIaRepository;
import tn.esprit.bawsla.forum.service.CacheTemporaire.Calcul;
import tn.esprit.bawsla.forum.service.FilService.Fil;

/**
 * Onglet « Tendances » du studio formateur (plan §4.7) et outils associés, pour le formateur DU cours ou l'admin :
 * <ul>
 *   <li>thèmes des questions des 30 derniers jours : K-Means sur les embeddings (k par silhouette, voir
 *       {@link Regroupement}), chaque groupe nommé par l'IA à partir de ses titres seulement ; mis en cache 1 h ;</li>
 *   <li>questions par chapitre, suggestions « Ajouter à la FAQ » (fils résolus les plus vus), demandes de réponse
 *       générale (signal de contenu manquant) ;</li>
 *   <li>apprenants bloqués : questions sans réponse depuis plus de 24 h, sous un pseudonyme ;</li>
 *   <li>passage d'un fil résolu en FAQ épinglée du cours.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TendancesService {

    static final int JOURS = 30;
    static final int QUESTIONS_MAX = 500;
    static final int TITRES_PAR_GROUPE = 10;
    static final int EXEMPLES = 3;
    static final int SUGGESTIONS_FAQ = 5;
    static final Duration DUREE_CACHE = Duration.ofHours(1);

    private final DiscussionRepository discussionRepository;
    private final ReponseIaRepository reponseIaRepository;
    private final ConversationVigieRepository conversationRepository;
    private final StatistiquesService statistiques;
    private final PresentationService presentation;
    private final AccesService acces;
    private final FilService fils;
    private final JournalService journal;
    private final IaService ia;
    private final Clock horloge;
    private final CacheTemporaire<Long, List<ThemeTendance>> themes;

    public TendancesService(DiscussionRepository discussionRepository, ReponseIaRepository reponseIaRepository,
                            ConversationVigieRepository conversationRepository, StatistiquesService statistiques,
                            PresentationService presentation, AccesService acces, FilService fils,
                            JournalService journal, IaService ia, Clock horloge) {
        this.discussionRepository = discussionRepository;
        this.reponseIaRepository = reponseIaRepository;
        this.conversationRepository = conversationRepository;
        this.statistiques = statistiques;
        this.presentation = presentation;
        this.acces = acces;
        this.fils = fils;
        this.journal = journal;
        this.ia = ia;
        this.horloge = horloge;
        this.themes = new CacheTemporaire<>(horloge);
    }

    public TendancesResponse tendances(Long coursId, CurrentUser user) {
        if (coursId == null) {
            throw new BadRequestException("Le cours est obligatoire");
        }
        acces.exigerModerateur(acces.contexte(user, coursId));
        LocalDateTime depuis = LocalDateTime.now(horloge).minusDays(JOURS);
        List<ThemeTendance> groupes = themes.obtenir(coursId, c -> new Calcul<>(calculerThemes(c, depuis),
                DUREE_CACHE));
        List<Discussion> faq = discussionRepository.resoluesPlusVues(coursId, StatutDiscussion.RESOLUE,
                PageRequest.of(0, SUGGESTIONS_FAQ));
        return new TendancesResponse(groupes, statistiques.calculer(coursId, JOURS).parChapitre(),
                presentation.resumes(faq, user), demandesGenerales(coursId, depuis), LocalDateTime.now(horloge));
    }

    public void oublierCaches() {
        themes.vider();
    }

    // ------------------------------------------------------------------ Thèmes

    List<ThemeTendance> calculerThemes(Long coursId, LocalDateTime depuis) {
        List<Discussion> questions = discussionRepository.creeesDepuis(coursId, depuis).stream()
                .filter(d -> d.getType() == TypeDiscussion.QUESTION && !d.isMasquee() && d.getEmbedding() != null)
                .sorted(Comparator.comparing(Discussion::getCreeLe).reversed()).toList();
        // On ne compare jamais deux modèles d'embedding : seul le modèle le plus représenté est gardé.
        String modele = questions.stream().collect(Collectors.groupingBy(Discussion::getModeleEmbedding,
                        Collectors.counting())).entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        List<Discussion> retenues = questions.stream().filter(d -> d.getModeleEmbedding().equals(modele))
                .limit(QUESTIONS_MAX).toList();
        Regroupement.Resultat r = Regroupement.meilleur(retenues.stream().map(Discussion::getEmbedding).toList());
        if (r == null) {
            return List.of();
        }
        List<List<Discussion>> groupes = new ArrayList<>();
        for (int c = 0; c < r.k(); c++) {
            groupes.add(new ArrayList<>());
        }
        for (int i = 0; i < retenues.size(); i++) {
            groupes.get(r.affectation()[i]).add(retenues.get(i));
        }
        groupes.removeIf(List::isEmpty);
        groupes.sort(Comparator.comparingInt((List<Discussion> g) -> g.size()).reversed());
        List<List<String>> titres = groupes.stream()
                .map(g -> g.stream().limit(TITRES_PAR_GROUPE).map(Discussion::getTitre).toList()).toList();
        List<String> noms = ia.nommerGroupes(titres).orElse(null);
        List<ThemeTendance> resultat = new ArrayList<>();
        for (int i = 0; i < groupes.size(); i++) {
            List<Discussion> g = groupes.get(i);
            String nom = noms == null ? "Thème " + (i + 1) : noms.get(i);
            resultat.add(new ThemeTendance(nom, g.size(), Math.round(1000.0 * g.size() / retenues.size()) / 1000.0,
                    g.stream().map(Discussion::getId).toList(),
                    g.stream().limit(EXEMPLES).map(Discussion::getTitre).toList()));
        }
        return resultat;
    }

    // ------------------------------------------------------------------ Contenu manquant

    /** Demandes de réponse générale (niveau 3) : le sujet de chaque fil ou conversation, par nombre de demandes. */
    private List<SerieStat> demandesGenerales(Long coursId, LocalDateTime depuis) {
        List<ReponseIa> generales = reponseIaRepository.creeesDepuis(coursId, depuis).stream()
                .filter(r -> r.getNiveau() == NiveauReponseIa.GENERAL).toList();
        Map<Long, String> titresFils = discussionRepository.findAllById(generales.stream()
                        .map(ReponseIa::getDiscussionId).filter(id -> id != null).distinct().toList()).stream()
                .collect(Collectors.toMap(Discussion::getId, Discussion::getTitre));
        Map<Long, String> titresConversations = conversationRepository.findAllById(generales.stream()
                        .map(ReponseIa::getConversationId).filter(id -> id != null).distinct().toList()).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getTitre() == null ? "Assistant privé"
                        : c.getTitre()));
        Map<String, Long> compte = generales.stream().map(r -> r.getDiscussionId() != null
                        ? titresFils.getOrDefault(r.getDiscussionId(), "Fil supprimé")
                        : titresConversations.getOrDefault(r.getConversationId(), "Assistant privé"))
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return compte.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10).map(e -> new SerieStat(Textes.extrait(e.getKey(), 120), e.getValue())).toList();
    }

    // ------------------------------------------------------------------ Apprenants bloqués

    /** Auteurs de questions sans réponse depuis plus de 24 h, sous un pseudonyme (« Apprenant A »…). */
    public List<ApprenantBloque> apprenantsBloques(Long coursId, CurrentUser user) {
        acces.exigerModerateur(acces.contexte(user, coursId));
        List<Discussion> questions = discussionRepository.sansReponseAvant(coursId, TypeDiscussion.QUESTION,
                LocalDateTime.now(horloge).minusHours(24));
        Map<Long, List<Discussion>> parAuteur = questions.stream()
                .collect(Collectors.groupingBy(Discussion::getAuteurId, LinkedHashMap::new, Collectors.toList()));
        List<List<Discussion>> tries = parAuteur.values().stream()
                .sorted(Comparator.comparingInt((List<Discussion> l) -> l.size()).reversed()).toList();
        List<ApprenantBloque> resultat = new ArrayList<>();
        for (int i = 0; i < tries.size(); i++) {
            List<Discussion> l = tries.get(i);
            resultat.add(new ApprenantBloque(pseudo(i), l.size(), l.get(0).getCreeLe(),
                    l.stream().map(Discussion::getId).toList()));
        }
        return resultat;
    }

    static String pseudo(int rang) {
        StringBuilder lettres = new StringBuilder();
        int n = rang;
        do {
            lettres.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return "Apprenant " + lettres;
    }

    // ------------------------------------------------------------------ FAQ du cours

    /** Un fil résolu devient une entrée de FAQ épinglée du cours. */
    @Transactional
    public DiscussionDetail ajouterFaq(Long coursId, Long discussionId, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        acces.exigerModerateur(fil.ctx());
        Discussion d = fil.discussion();
        if (!d.getCoursId().equals(coursId)) {
            throw new BadRequestException("Cette discussion n'appartient pas à ce cours");
        }
        if (d.getStatut() != StatutDiscussion.RESOLUE) {
            throw new BadRequestException("Seul un fil résolu peut entrer dans la FAQ");
        }
        d.setFaq(true);
        d.setEpinglee(true);
        journal.tracer(user, ActionModeration.EPINGLER, "DISCUSSION", discussionId, coursId, "Ajout à la FAQ");
        return presentation.detail(d, fil.ctx());
    }
}

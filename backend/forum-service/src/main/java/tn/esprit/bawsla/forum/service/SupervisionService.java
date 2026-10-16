package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.AdminStatistiquesResponse;
import tn.esprit.bawsla.forum.dto.CoursActivite;
import tn.esprit.bawsla.forum.dto.SerieStat;
import tn.esprit.bawsla.forum.dto.SupervisionResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.Signalement;
import tn.esprit.bawsla.forum.entity.enums.CategorieModeration;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseIaRepository;
import tn.esprit.bawsla.forum.repository.SignalementRepository;
import tn.esprit.bawsla.forum.service.AnnuaireService.CoursInfo;

/**
 * Espace admin (plan §7.7) : vue d'ensemble de la plateforme (indicateurs, cours les plus actifs, cours où personne
 * ne répond) et supervision de La Vigie (appels, jetons estimés, fournisseurs, niveaux, abstention, avis utiles,
 * injections bloquées, données personnelles, version de prompt, sujets de réponse générale).
 */
@Service
@Transactional(readOnly = true)
public class SupervisionService {

    static final int CLASSEMENT = 5;
    private static final Set<StatutReponseIa> PRODUITES = EnumSet.of(StatutReponseIa.GENEREE,
            StatutReponseIa.VALIDEE, StatutReponseIa.CORRIGEE, StatutReponseIa.REJETEE, StatutReponseIa.HORS_COURS);

    private final DiscussionRepository discussionRepository;
    private final ReponseIaRepository reponseIaRepository;
    private final SignalementRepository signalementRepository;
    private final StatistiquesService statistiques;
    private final AnnuaireService annuaire;
    private final AccesService acces;
    private final Clock horloge;

    public SupervisionService(DiscussionRepository discussionRepository, ReponseIaRepository reponseIaRepository,
                              SignalementRepository signalementRepository, StatistiquesService statistiques,
                              AnnuaireService annuaire, AccesService acces, Clock horloge) {
        this.discussionRepository = discussionRepository;
        this.reponseIaRepository = reponseIaRepository;
        this.signalementRepository = signalementRepository;
        this.statistiques = statistiques;
        this.annuaire = annuaire;
        this.acces = acces;
        this.horloge = horloge;
    }

    public AdminStatistiquesResponse statistiques(Integer periode, CurrentUser user) {
        acces.exigerAdmin(user);
        int jours = jours(periode);
        List<Discussion> questions = discussionRepository.creeesDepuis(null, LocalDateTime.now(horloge)
                .minusDays(jours)).stream().filter(d -> d.getType() == TypeDiscussion.QUESTION && !d.isMasquee())
                .toList();
        List<CoursActivite> parCours = questions.stream().collect(Collectors.groupingBy(Discussion::getCoursId))
                .entrySet().stream().map(e -> activite(e.getKey(), e.getValue())).toList();
        List<CoursActivite> actifs = parCours.stream()
                .sorted(Comparator.comparingLong(CoursActivite::questions).reversed()).limit(CLASSEMENT).toList();
        List<CoursActivite> sansReponse = parCours.stream().filter(c -> c.sansReponse() > 0)
                .sorted(Comparator.comparingLong(CoursActivite::sansReponse).reversed()
                        .thenComparingDouble(CoursActivite::tauxResolution)).limit(CLASSEMENT).toList();
        return new AdminStatistiquesResponse(statistiques.calculer(null, jours),
                signalementRepository.countByStatut(StatutSignalement.OUVERT), actifs, sansReponse);
    }

    /** versionPromptActive = version du prompt de la réponse la plus récente (null si aucune réponse). */
    public SupervisionResponse supervision(Integer periode, CurrentUser user) {
        acces.exigerAdmin(user);
        LocalDateTime depuis = LocalDateTime.now(horloge).minusDays(jours(periode));
        List<ReponseIa> reponses = reponseIaRepository.creeesDepuis(null, depuis).stream()
                .filter(r -> PRODUITES.contains(r.getStatut()) || r.getStatut() == StatutReponseIa.ERREUR).toList();
        Map<String, Long> parFournisseur = reponses.stream().filter(r -> r.getFournisseur() != null)
                .collect(Collectors.groupingBy(ReponseIa::getFournisseur, TreeMap::new, Collectors.counting()));
        Map<NiveauReponseIa, Long> parNiveau = new EnumMap<>(NiveauReponseIa.class);
        reponses.stream().filter(r -> PRODUITES.contains(r.getStatut()))
                .forEach(r -> parNiveau.merge(r.getNiveau(), 1L, Long::sum));
        long sourcees = reponses.stream().filter(r -> PRODUITES.contains(r.getStatut())
                && r.getNiveau() != NiveauReponseIa.GENERAL).count();
        long abstentions = reponses.stream().filter(r -> r.getStatut() == StatutReponseIa.HORS_COURS).count();
        long oui = reponses.stream().mapToLong(ReponseIa::getUtileOui).sum();
        long non = reponses.stream().mapToLong(ReponseIa::getUtileNon).sum();
        List<Signalement> signalementsIa = signalementRepository.findAll().stream()
                .filter(s -> s.getAuteurId() == null && s.getCategorieIa() != null && !s.getCreeLe().isBefore(depuis))
                .toList();
        String version = reponses.stream().filter(r -> r.getVersionPrompt() != null)
                .max(Comparator.comparing(ReponseIa::getCreeLe)).map(ReponseIa::getVersionPrompt)
                .orElse(null);
        List<SerieStat> parJour = reponses.stream().collect(Collectors.groupingBy(
                        r -> r.getCreeLe().toLocalDate().toString(), TreeMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new SerieStat(e.getKey(), e.getValue())).toList();
        Map<Long, String> titres = discussionRepository.findAllById(reponses.stream()
                        .filter(r -> r.getNiveau() == NiveauReponseIa.GENERAL && r.getDiscussionId() != null)
                        .map(ReponseIa::getDiscussionId).distinct().toList()).stream()
                .collect(Collectors.toMap(Discussion::getId, Discussion::getTitre));
        List<SerieStat> sujets = titres.values().stream().map(t -> new SerieStat(Textes.extrait(t, 120), 1))
                .limit(10).toList();
        return new SupervisionResponse(reponses.size(),
                reponses.stream().mapToLong(r -> r.getJetonsEstimes() == null ? 0 : r.getJetonsEstimes()).sum(),
                parFournisseur, parNiveau, taux(abstentions, sourcees), taux(oui, oui + non),
                signalementsIa.stream().filter(s -> s.getCategorieIa() == CategorieModeration.INJECTION).count(),
                signalementsIa.stream().filter(s -> s.getCategorieIa() == CategorieModeration.DONNEES_PERSONNELLES)
                        .count(),
                version, parJour, sujets);
    }

    private CoursActivite activite(Long coursId, List<Discussion> questions) {
        CoursInfo cours = annuaire.cours(coursId).orElse(new CoursInfo(coursId, "Cours n° " + coursId, null, false));
        long sansReponse = questions.stream().filter(d -> d.getNbReponses() == 0
                && d.getStatut() != StatutDiscussion.RESOLUE).count();
        long resolues = questions.stream().filter(d -> d.getStatut() == StatutDiscussion.RESOLUE).count();
        return new CoursActivite(coursId, cours.titre(), cours.enseignantId(), questions.size(), sansReponse,
                taux(resolues, questions.size()));
    }

    private static int jours(Integer periode) {
        int jours = periode == null ? 30 : periode;
        if (jours != 7 && jours != 30 && jours != 90) {
            throw new BadRequestException("Période en jours : 7, 30 ou 90");
        }
        return jours;
    }

    private static double taux(long part, long total) {
        return total == 0 ? 0 : Math.round(1000.0 * part / total) / 1000.0;
    }
}

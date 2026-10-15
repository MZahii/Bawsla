package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.SerieStat;
import tn.esprit.bawsla.forum.dto.StatistiquesResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseIaRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;

/**
 * Indicateurs du forum d'un cours (formateur du cours ou admin) ou de toute la plateforme (admin) :
 * questions de la période, sans réponse, délai moyen de première réponse, taux de résolution,
 * part couverte par La Vigie, part de ses réponses jugées utiles, questions par chapitre et par semaine.
 */
@Service
@Transactional(readOnly = true)
public class StatistiquesService {

    private static final Set<StatutReponseIa> VIGIE_REPONDU = EnumSet.of(StatutReponseIa.GENEREE,
            StatutReponseIa.VALIDEE, StatutReponseIa.CORRIGEE);

    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final ReponseIaRepository reponseIaRepository;
    private final AccesService acces;
    private final Clock horloge;

    public StatistiquesService(DiscussionRepository discussionRepository, ReponseRepository reponseRepository,
                               ReponseIaRepository reponseIaRepository, AccesService acces, Clock horloge) {
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.reponseIaRepository = reponseIaRepository;
        this.acces = acces;
        this.horloge = horloge;
    }

    /** periode en jours (7, 30 ou 90 ; 30 par défaut). Sans cours : réservé à l'admin. */
    public StatistiquesResponse statistiques(Long coursId, Integer periode, CurrentUser user) {
        if (coursId == null) {
            acces.exigerAdmin(user);
        } else {
            acces.exigerModerateur(acces.contexte(user, coursId));
        }
        int jours = periode == null ? 30 : periode;
        if (jours != 7 && jours != 30 && jours != 90) {
            throw new BadRequestException("Période en jours : 7, 30 ou 90");
        }
        return calculer(coursId, jours);
    }

    StatistiquesResponse calculer(Long coursId, int jours) {
        LocalDateTime depuis = LocalDateTime.now(horloge).minusDays(jours);
        List<Discussion> questions = discussionRepository.creeesDepuis(coursId, depuis).stream()
                .filter(d -> d.getType() == TypeDiscussion.QUESTION && !d.isMasquee()).toList();
        long sansReponse = discussionRepository.sansReponse(coursId, TypeDiscussion.QUESTION);
        if (questions.isEmpty()) {
            return new StatistiquesResponse(0, sansReponse, null, 0, 0, 0, List.of(), List.of());
        }
        List<Long> ids = questions.stream().map(Discussion::getId).toList();

        Map<Long, LocalDateTime> premieres = new HashMap<>();
        for (Object[] ligne : reponseRepository.premieresReponses(ids)) {
            premieres.put((Long) ligne[0], (LocalDateTime) ligne[1]);
        }
        Double delai = questions.stream().filter(d -> premieres.containsKey(d.getId()))
                .mapToDouble(d -> Duration.between(d.getCreeLe(), premieres.get(d.getId())).toMinutes() / 60.0)
                .average().stream().boxed().findFirst().map(h -> Math.round(h * 10) / 10.0).orElse(null);
        long resolues = questions.stream().filter(d -> d.getStatut() == StatutDiscussion.RESOLUE).count();

        Set<Long> idsQuestions = Set.copyOf(ids);
        List<ReponseIa> vigie = reponseIaRepository.creeesDepuis(coursId, depuis).stream()
                .filter(r -> r.getDiscussionId() != null && idsQuestions.contains(r.getDiscussionId())).toList();
        long couvertes = vigie.stream().filter(r -> VIGIE_REPONDU.contains(r.getStatut()))
                .map(ReponseIa::getDiscussionId).distinct().count();
        long oui = vigie.stream().mapToLong(ReponseIa::getUtileOui).sum();
        long non = vigie.stream().mapToLong(ReponseIa::getUtileNon).sum();

        return new StatistiquesResponse(questions.size(), sansReponse, delai,
                taux(resolues, questions.size()), taux(couvertes, questions.size()), taux(oui, oui + non),
                parChapitre(questions), parSemaine(questions));
    }

    private static List<SerieStat> parChapitre(List<Discussion> questions) {
        Map<String, Long> compte = questions.stream().collect(Collectors.groupingBy(
                d -> d.getChapitreId() == null ? "Sans chapitre" : "Chapitre n° " + d.getChapitreId(),
                TreeMap::new, Collectors.counting()));
        return compte.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new SerieStat(e.getKey(), e.getValue())).toList();
    }

    private static List<SerieStat> parSemaine(List<Discussion> questions) {
        Map<String, Long> compte = questions.stream().collect(Collectors.groupingBy(d -> {
            LocalDate jour = d.getCreeLe().toLocalDate();
            return String.format("%d-S%02d", jour.get(IsoFields.WEEK_BASED_YEAR),
                    jour.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        }, TreeMap::new, Collectors.counting()));
        return compte.entrySet().stream().map(e -> new SerieStat(e.getKey(), e.getValue())).toList();
    }

    private static double taux(long part, long total) {
        return total == 0 ? 0 : Math.round(1000.0 * part / total) / 1000.0;
    }
}

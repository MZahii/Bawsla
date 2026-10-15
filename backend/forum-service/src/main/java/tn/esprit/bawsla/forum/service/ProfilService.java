package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.ActiviteResponse;
import tn.esprit.bawsla.forum.dto.BadgeResponse;
import tn.esprit.bawsla.forum.dto.ClassementEntree;
import tn.esprit.bawsla.forum.dto.DiscussionResume;
import tn.esprit.bawsla.forum.dto.EvenementReputationResponse;
import tn.esprit.bawsla.forum.dto.ProfilResponse;
import tn.esprit.bawsla.forum.dto.ReputationResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Reputation;
import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.repository.AbonnementRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.EvenementReputationRepository;
import tn.esprit.bawsla.forum.repository.FavoriRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.ReputationRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;
import tn.esprit.bawsla.forum.service.AnnuaireService.MembreInfo;
import tn.esprit.bawsla.forum.service.PresentationService.Auteurs;

/**
 * Carte de survol d'un auteur, page « Mon activité », réputation détaillée et classement hebdomadaire par cours.
 * Les statistiques publiques d'un membre ne comptent pas ses messages anonymes (sinon l'anonymat fuirait).
 */
@Service
@Transactional(readOnly = true)
public class ProfilService {

    private static final int TAILLE_LISTE = 20;
    private static final int TAILLE_HISTORIQUE = 50;
    private static final int TAILLE_CLASSEMENT = 10;

    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final AbonnementRepository abonnementRepository;
    private final FavoriRepository favoriRepository;
    private final ReputationRepository reputationRepository;
    private final EvenementReputationRepository evenementRepository;
    private final BadgeService badges;
    private final AnnuaireService annuaire;
    private final AccesService acces;
    private final PresentationService presentation;
    private final Clock horloge;

    public ProfilService(DiscussionRepository discussionRepository, ReponseRepository reponseRepository,
                         AbonnementRepository abonnementRepository, FavoriRepository favoriRepository,
                         ReputationRepository reputationRepository, EvenementReputationRepository evenementRepository,
                         BadgeService badges, AnnuaireService annuaire, AccesService acces,
                         PresentationService presentation, Clock horloge) {
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.abonnementRepository = abonnementRepository;
        this.favoriRepository = favoriRepository;
        this.reputationRepository = reputationRepository;
        this.evenementRepository = evenementRepository;
        this.badges = badges;
        this.annuaire = annuaire;
        this.acces = acces;
        this.presentation = presentation;
        this.horloge = horloge;
    }

    public ProfilResponse profil(Long userId, CurrentUser lecteur) {
        acces.exigerConnecte(lecteur);
        MembreInfo membre = annuaire.membre(userId);
        int points = points(userId);
        NiveauReputation niveau = NiveauReputation.depuisPoints(points);
        List<BadgeResponse> obtenus = badges.progression(userId).stream().filter(BadgeResponse::obtenu).toList();
        List<String> etiquettes = discussionRepository.etiquettesFavorites(userId, PageRequest.of(0, 3)).stream()
                .map(ligne -> (String) ligne[0]).toList();
        boolean complet = userId.equals(lecteur.id()) || lecteur.isAdmin();
        long questions = complet
                ? discussionRepository.countByAuteurIdAndTypeAndSupprimeeLeIsNull(userId, TypeDiscussion.QUESTION)
                : discussionRepository.countByAuteurIdAndTypeAndAnonymeFalseAndSupprimeeLeIsNull(userId,
                        TypeDiscussion.QUESTION);
        long reponses = complet ? reponseRepository.countByAuteurIdAndSupprimeeLeIsNull(userId)
                : reponseRepository.countByAuteurIdAndAnonymeFalseAndSupprimeeLeIsNull(userId);
        long acceptees = complet ? reponseRepository.countByAuteurIdAndAccepteeTrueAndSupprimeeLeIsNull(userId)
                : reponseRepository.countByAuteurIdAndAccepteeTrueAndAnonymeFalseAndSupprimeeLeIsNull(userId);
        return new ProfilResponse(userId, membre.nom(), membre.role(), niveau, niveau.getLibelle(), points, obtenus,
                etiquettes, questions, reponses, acceptees);
    }

    public ActiviteResponse activite(CurrentUser user) {
        acces.exigerConnecte(user);
        PageRequest page = PageRequest.of(0, TAILLE_LISTE);
        List<DiscussionResume> questions = presentation.resumes(discussionRepository.deLAuteur(user.id(), page),
                user);
        List<DiscussionResume> repondues = resumes(reponseRepository.discussionsRepondues(user.id()), user);
        List<DiscussionResume> favoris = resumes(favoriRepository.discussionsFavorites(user.id()), user);
        List<DiscussionResume> suivis = resumes(abonnementRepository.discussionsSuivies(user.id()), user);
        return new ActiviteResponse(questions, repondues, favoris, suivis, reputation(user));
    }

    public ReputationResponse reputation(CurrentUser user) {
        acces.exigerConnecte(user);
        int points = points(user.id());
        NiveauReputation niveau = NiveauReputation.depuisPoints(points);
        NiveauReputation suivant = niveau.suivant();
        List<EvenementReputationResponse> historique = evenementRepository
                .findByUserIdOrderByCreeLeDescIdDesc(user.id(), PageRequest.of(0, TAILLE_HISTORIQUE)).stream()
                .map(EvenementReputationResponse::from).toList();
        return new ReputationResponse(points, niveau, niveau.getLibelle(), suivant,
                suivant == null ? null : suivant.getSeuil(), historique, badges.progression(user.id()));
    }

    /** Classement des contributeurs d'un cours : points gagnés sur la semaine (7 jours) ou le mois (30 jours). */
    public List<ClassementEntree> classement(Long coursId, String periode, CurrentUser user) {
        if (coursId == null) {
            throw new BadRequestException("Le cours est obligatoire");
        }
        Contexte ctx = acces.contexte(user, coursId);
        int jours = switch (periode == null ? "semaine" : periode) {
            case "semaine" -> 7;
            case "mois" -> 30;
            default -> throw new BadRequestException("Période inconnue : " + periode);
        };
        LocalDateTime depuis = LocalDateTime.now(horloge).minusDays(jours);
        Auteurs auteurs = presentation.auteurs(user);
        List<ClassementEntree> classement = new ArrayList<>();
        for (Object[] ligne : evenementRepository.classement(coursId, depuis, PageRequest.of(0, TAILLE_CLASSEMENT))) {
            Long userId = (Long) ligne[0];
            int points = ((Number) ligne[1]).intValue();
            classement.add(new ClassementEntree(classement.size() + 1,
                    auteurs.de(userId, false, ctx.cours(), ctx.moderateur()), points));
        }
        return classement;
    }

    private List<DiscussionResume> resumes(List<Long> ids, CurrentUser user) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Long> premiers = ids.subList(0, Math.min(ids.size(), TAILLE_LISTE));
        List<Discussion> discussions = discussionRepository.visibles(premiers, PageRequest.of(0, TAILLE_LISTE));
        return presentation.resumes(discussions, user);
    }

    private int points(Long userId) {
        return reputationRepository.findById(userId).map(Reputation::getPoints).orElse(0);
    }
}

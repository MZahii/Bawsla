package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ForbiddenException;
import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.entity.Sanction;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.entity.enums.TypeSanction;
import tn.esprit.bawsla.forum.exception.TropDeRequetesException;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.SanctionRepository;
import tn.esprit.bawsla.forum.service.AnnuaireService.CoursInfo;

/**
 * Droits du forum, tous vérifiés côté backend (plan §5.4). Les guards Angular ne sont qu'un confort.
 * <ul>
 *   <li>Lire : tout utilisateur connecté qui voit le cours (404 de cours-service = pas d'accès).</li>
 *   <li>Modérer : le formateur DU cours (enseignantId du contrat 1) ou un ADMIN. Si cours-service ne répond pas,
 *       seul l'ADMIN modère.</li>
 *   <li>Écrire : pas de sanction LECTURE_SEULE ou SUSPENSION active, forum du cours ouvert, limites anti-flood.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AccesService {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
    private static final List<TypeSanction> SANCTIONS_ECRITURE = List.of(TypeSanction.LECTURE_SEULE,
            TypeSanction.SUSPENSION);

    /** Utilisateur et cours d'une requête, avec le droit de modérer déjà calculé. */
    public record Contexte(CurrentUser user, CoursInfo cours, boolean moderateur) {

        public Long coursId() {
            return cours.id();
        }
    }

    private final AnnuaireService annuaire;
    private final ParametresService parametres;
    private final SanctionRepository sanctionRepository;
    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final CommentaireRepository commentaireRepository;
    private final Clock horloge;

    public AccesService(AnnuaireService annuaire, ParametresService parametres, SanctionRepository sanctionRepository,
                        DiscussionRepository discussionRepository, ReponseRepository reponseRepository,
                        CommentaireRepository commentaireRepository, Clock horloge) {
        this.annuaire = annuaire;
        this.parametres = parametres;
        this.sanctionRepository = sanctionRepository;
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.commentaireRepository = commentaireRepository;
        this.horloge = horloge;
    }

    /** Rôle connu de la plateforme (un rôle forgé ou inconnu est refusé). */
    public void exigerConnecte(CurrentUser user) {
        user.requireAnyRole(CurrentUser.ADMIN, CurrentUser.ENSEIGNANT, CurrentUser.ETUDIANT);
    }

    public void exigerAdmin(CurrentUser user) {
        user.requireAnyRole(CurrentUser.ADMIN);
    }

    /** Contexte de lecture d'un cours : 404 si cours-service dit que le cours n'existe pas pour cet utilisateur. */
    public Contexte contexte(CurrentUser user, Long coursId) {
        exigerConnecte(user);
        CoursInfo cours = annuaire.cours(coursId)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable (id=" + coursId + ")"));
        return new Contexte(user, cours, estModerateur(user, cours));
    }

    public boolean estModerateur(CurrentUser user, CoursInfo cours) {
        if (user.isAdmin()) {
            return true;
        }
        return CurrentUser.ENSEIGNANT.equals(user.role()) && cours.enseignantId() != null
                && cours.enseignantId().equals(user.id());
    }

    public void exigerModerateur(Contexte ctx) {
        if (!ctx.moderateur()) {
            throw new ForbiddenException("Action réservée au formateur du cours ou à l'administration");
        }
    }

    /** Écriture : sanction active, forum fermé (sauf modérateur). */
    public void exigerEcriture(Contexte ctx) {
        exigerSansSanction(ctx.user());
        if (!ctx.moderateur() && !parametres.cours(ctx.coursId()).isOuvert()) {
            throw new ForbiddenException("Le forum de ce cours est fermé pour le moment");
        }
    }

    public void exigerSansSanction(CurrentUser user) {
        List<Sanction> actives = sanctionRepository.actives(user.id(), SANCTIONS_ECRITURE,
                LocalDateTime.now(horloge));
        if (!actives.isEmpty()) {
            Sanction s = actives.get(0);
            String jusqua = s.getDateFin() == null ? "jusqu'à nouvel ordre"
                    : "jusqu'au " + FORMAT_DATE.format(s.getDateFin());
            throw new ForbiddenException("Ton accès en écriture au forum est suspendu " + jusqua);
        }
    }

    /** Anonymat : réservé aux apprenants, et seulement si le forum du cours l'autorise. */
    public void exigerAnonymatPermis(Contexte ctx) {
        if (!CurrentUser.ETUDIANT.equals(ctx.user().role())) {
            throw new BadRequestException("Seuls les apprenants peuvent publier anonymement");
        }
        if (!parametres.cours(ctx.coursId()).isAnonymatAutorise()) {
            throw new BadRequestException("L'anonymat n'est pas autorisé dans le forum de ce cours");
        }
    }

    /**
     * Limites anti-flood (plan §3.7) : 5 questions et 30 messages par heure et par utilisateur.
     * Le formateur du cours et l'admin n'y sont pas soumis.
     */
    public void exigerSousLimites(Contexte ctx, boolean nouvelleQuestion) {
        if (ctx.moderateur()) {
            return;
        }
        Long id = ctx.user().id();
        LocalDateTime depuis = LocalDateTime.now(horloge).minusHours(1);
        if (nouvelleQuestion && discussionRepository.countByAuteurIdAndTypeAndCreeLeAfter(id, TypeDiscussion.QUESTION,
                depuis) >= parametres.entier(ParametresService.QUESTIONS_PAR_HEURE)) {
            throw new TropDeRequetesException("Tu as déjà posé "
                    + parametres.entier(ParametresService.QUESTIONS_PAR_HEURE)
                    + " questions cette heure-ci. Réessaie un peu plus tard.");
        }
        long messages = discussionRepository.countByAuteurIdAndCreeLeAfter(id, depuis)
                + reponseRepository.countByAuteurIdAndCreeLeAfter(id, depuis)
                + commentaireRepository.countByAuteurIdAndCreeLeAfter(id, depuis);
        if (messages >= parametres.entier(ParametresService.MESSAGES_PAR_HEURE)) {
            throw new TropDeRequetesException("Tu as publié beaucoup de messages cette heure-ci. "
                    + "Réessaie un peu plus tard.");
        }
    }
}

package tn.esprit.bawsla.forum.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.CompteurResponse;
import tn.esprit.bawsla.forum.dto.NotificationResponse;
import tn.esprit.bawsla.forum.dto.PageResponse;
import tn.esprit.bawsla.forum.entity.Abonnement;
import tn.esprit.bawsla.forum.entity.Commentaire;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Notification;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.CodeBadge;
import tn.esprit.bawsla.forum.entity.enums.TypeNotification;
import tn.esprit.bawsla.forum.evenement.CommentaireCree;
import tn.esprit.bawsla.forum.evenement.MessageMasque;
import tn.esprit.bawsla.forum.evenement.ReponseAcceptee;
import tn.esprit.bawsla.forum.evenement.ReponseApprouvee;
import tn.esprit.bawsla.forum.evenement.ReponseCreee;
import tn.esprit.bawsla.forum.repository.AbonnementRepository;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.NotificationRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.service.PresentationService.Auteurs;

/**
 * Notifications internes au forum (plan §3.4) : réponse dans un fil suivi, commentaire, mention @Prénom Nom,
 * réponse acceptée, approbation, message masqué, badge ; La Vigie et la modération en ajoutent aux lots suivants.
 * Personne n'est notifié de sa propre action ; un message anonyme n'expose jamais son auteur (acteur absent).
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final int LONGUEUR_EXTRAIT = 140;
    private static final int TAILLE_PAGE = 20;

    private final NotificationRepository repository;
    private final AbonnementRepository abonnementRepository;
    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final CommentaireRepository commentaireRepository;
    private final AnnuaireService annuaire;
    private final AccesService acces;
    private final PresentationService presentation;

    public NotificationService(NotificationRepository repository, AbonnementRepository abonnementRepository,
                               DiscussionRepository discussionRepository, ReponseRepository reponseRepository,
                               CommentaireRepository commentaireRepository, AnnuaireService annuaire,
                               AccesService acces, PresentationService presentation) {
        this.repository = repository;
        this.abonnementRepository = abonnementRepository;
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.commentaireRepository = commentaireRepository;
        this.annuaire = annuaire;
        this.acces = acces;
        this.presentation = presentation;
    }

    @Transactional
    public void notifier(Long destinataireId, TypeNotification type, Long discussionId, Long reponseId,
                         Long acteurId, String extrait) {
        if (destinataireId == null || destinataireId.equals(acteurId)) {
            return;
        }
        Notification n = new Notification();
        n.setDestinataireId(destinataireId);
        n.setType(type);
        n.setDiscussionId(discussionId);
        n.setReponseId(reponseId);
        n.setActeurId(acteurId);
        n.setExtrait(extrait == null ? null : Textes.extrait(extrait, LONGUEUR_EXTRAIT));
        repository.save(n);
    }

    // ------------------------------------------------------------------ API

    public PageResponse<NotificationResponse> lister(CurrentUser user, boolean nonLues, Integer page) {
        acces.exigerConnecte(user);
        int numero = page == null ? 0 : Math.max(0, page);
        PageRequest pageable = PageRequest.of(numero, TAILLE_PAGE);
        Page<Notification> resultat = nonLues
                ? repository.findByDestinataireIdAndLueFalseOrderByCreeLeDescIdDesc(user.id(), pageable)
                : repository.findByDestinataireIdOrderByCreeLeDescIdDesc(user.id(), pageable);
        Set<Long> discussionIds = resultat.getContent().stream().map(Notification::getDiscussionId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> titres = discussionRepository.findAllById(discussionIds).stream()
                .collect(Collectors.toMap(Discussion::getId, Discussion::getTitre));
        Auteurs auteurs = presentation.auteurs(user);
        List<NotificationResponse> contenu = resultat.getContent().stream()
                .map(n -> new NotificationResponse(n.getId(), n.getType(), n.getDiscussionId(), n.getReponseId(),
                        titres.get(n.getDiscussionId()), auteurs.de(n.getActeurId(), false, null, false),
                        n.getExtrait(), n.isLue(), n.getCreeLe()))
                .toList();
        return PageResponse.of(contenu, numero, TAILLE_PAGE, resultat.getTotalElements());
    }

    public CompteurResponse compteur(CurrentUser user) {
        acces.exigerConnecte(user);
        return new CompteurResponse(repository.countByDestinataireIdAndLueFalse(user.id()));
    }

    /** Marque lues les notifications indiquées, ou toutes si la liste est vide. */
    @Transactional
    public CompteurResponse marquerLues(CurrentUser user, Collection<Long> ids) {
        acces.exigerConnecte(user);
        if (ids == null || ids.isEmpty()) {
            repository.toutMarquerLu(user.id());
        } else {
            repository.marquerLues(user.id(), ids);
        }
        return compteur(user);
    }

    // ------------------------------------------------------------------ Événements

    @EventListener
    @Transactional
    public void reponseCreee(ReponseCreee e) {
        Long acteur = e.anonyme() ? null : e.auteurId();
        Set<Long> mentionnes = mentionnes(e.discussionId(), e.auteurId(), e.contenu());
        mentionnes.forEach(id -> notifierSaufAuteur(id, e.auteurId(), TypeNotification.MENTION, e.discussionId(),
                e.reponseId(), acteur, e.contenu()));
        for (Abonnement a : abonnementRepository.findByDiscussionId(e.discussionId())) {
            if (!mentionnes.contains(a.getUserId())) {
                notifierSaufAuteur(a.getUserId(), e.auteurId(), TypeNotification.REPONSE, e.discussionId(),
                        e.reponseId(), acteur, e.contenu());
            }
        }
    }

    @EventListener
    @Transactional
    public void commentaireCree(CommentaireCree e) {
        Set<Long> mentionnes = mentionnes(e.discussionId(), e.auteurId(), e.contenu());
        mentionnes.forEach(id -> notifier(id, TypeNotification.MENTION, e.discussionId(), e.reponseId(),
                e.auteurId(), e.contenu()));
        if (!mentionnes.contains(e.auteurCibleId())) {
            notifier(e.auteurCibleId(), TypeNotification.COMMENTAIRE, e.discussionId(), e.reponseId(), e.auteurId(),
                    e.contenu());
        }
    }

    @EventListener
    @Transactional
    public void reponseAcceptee(ReponseAcceptee e) {
        if (e.reponseId() != null) {
            notifier(e.auteurReponseId(), TypeNotification.REPONSE_ACCEPTEE, e.discussionId(), e.reponseId(),
                    e.accepteurId(), null);
        }
    }

    @EventListener
    @Transactional
    public void reponseApprouvee(ReponseApprouvee e) {
        if (e.approuvee()) {
            notifier(e.auteurReponseId(), TypeNotification.APPROBATION, e.discussionId(), e.reponseId(),
                    e.formateurId(), null);
        }
    }

    @EventListener
    @Transactional
    public void messageMasque(MessageMasque e) {
        if (e.masque()) {
            notifier(e.auteurId(), TypeNotification.MESSAGE_MASQUE, e.discussionId(),
                    e.cibleType() == CibleType.REPONSE ? e.cibleId() : null, null,
                    e.automatique() ? "Masqué automatiquement en attendant la décision du formateur"
                            : "Masqué par la modération");
        }
    }

    @Transactional
    public void badge(Long userId, CodeBadge code) {
        notifier(userId, TypeNotification.BADGE, null, null, null, "Badge obtenu : " + code.getLibelle());
    }

    // ------------------------------------------------------------------ Mentions

    /**
     * Participants du fil (auteur de la question, auteurs des réponses et des commentaires) dont le nom complet
     * apparaît précédé d'un @ dans le texte. Les participants anonymes ne sont pas mentionnables.
     */
    Set<Long> mentionnes(Long discussionId, Long auteurId, String texte) {
        Set<Long> resultat = new LinkedHashSet<>();
        if (texte == null || !texte.contains("@")) {
            return resultat;
        }
        String bas = texte.toLowerCase(Locale.ROOT);
        Map<Long, String> noms = new HashMap<>();
        Function<Long, String> nom = id -> noms.computeIfAbsent(id,
                k -> annuaire.membre(k).nom().toLowerCase(Locale.ROOT));
        for (Long participant : participants(discussionId)) {
            if (!participant.equals(auteurId) && bas.contains("@" + nom.apply(participant))) {
                resultat.add(participant);
            }
        }
        return resultat;
    }

    private Set<Long> participants(Long discussionId) {
        Set<Long> ids = new LinkedHashSet<>();
        discussionRepository.findById(discussionId).filter(d -> !d.isAnonyme())
                .ifPresent(d -> ids.add(d.getAuteurId()));
        reponseRepository.findByDiscussionIdAndSupprimeeLeIsNull(discussionId).stream()
                .filter(r -> !r.isAnonyme()).map(Reponse::getAuteurId).forEach(ids::add);
        commentaireRepository.findByDiscussionIdAndSupprimeLeIsNullOrderByCreeLeAsc(discussionId).stream()
                .map(Commentaire::getAuteurId).forEach(ids::add);
        return ids;
    }

    private void notifierSaufAuteur(Long destinataire, Long auteurReel, TypeNotification type, Long discussionId,
                                    Long reponseId, Long acteurAffiche, String extrait) {
        if (!destinataire.equals(auteurReel)) {
            notifier(destinataire, type, discussionId, reponseId, acteurAffiche, extrait);
        }
    }
}

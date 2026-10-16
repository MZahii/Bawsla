package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.client.ReformulerIaDto;
import tn.esprit.bawsla.forum.common.exception.ForbiddenException;
import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.BrouillonQuestionResponse;
import tn.esprit.bawsla.forum.dto.ConversationResponse;
import tn.esprit.bawsla.forum.dto.MessageVigieRequest;
import tn.esprit.bawsla.forum.dto.MessageVigieResponse;
import tn.esprit.bawsla.forum.dto.VigieResponse;
import tn.esprit.bawsla.forum.entity.ConversationVigie;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.MessageVigie;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.enums.ModeVigie;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.RoleMessage;
import tn.esprit.bawsla.forum.exception.TropDeRequetesException;
import tn.esprit.bawsla.forum.repository.ConversationVigieRepository;
import tn.esprit.bawsla.forum.repository.MessageVigieRepository;
import tn.esprit.bawsla.forum.repository.ReponseIaRepository;
import tn.esprit.bawsla.forum.service.VigieService.Production;

/**
 * Assistant privé « Demander au cours » (plan §4.3) : conversation avec La Vigie sans rien publier.
 * <ul>
 *   <li>Les 6 derniers échanges servent à rendre une question de suivi autonome (« et avec un RIGHT JOIN ? »).</li>
 *   <li>Même pipeline que dans les fils : sources citées, confiance, abstention ; modes Réponse directe, Guide-moi
 *       (forcé en mode examen) et réponse générale (si elle est permise dans le cours).</li>
 *   <li>« Publier comme question » prépare un brouillon ; publié avec {@code conversationId}, le fil reçoit la
 *       réponse de La Vigie déjà obtenue au lieu d'en générer une nouvelle.</li>
 * </ul>
 * Une conversation n'est visible que par son auteur.
 */
@Service
@Transactional(readOnly = true)
public class AssistantService {

    static final int ECHANGES_GARDES = 6;
    static final int QUESTIONS_PAR_HEURE = 30;

    private final ConversationVigieRepository conversationRepository;
    private final MessageVigieRepository messageRepository;
    private final ReponseIaRepository reponseIaRepository;
    private final VigieService vigie;
    private final AccesService acces;
    private final ParametresService parametres;
    private final RechercheService recherche;
    private final AnnuaireService annuaire;
    private final Clock horloge;

    public AssistantService(ConversationVigieRepository conversationRepository, MessageVigieRepository messageRepository,
                            ReponseIaRepository reponseIaRepository, VigieService vigie, AccesService acces,
                            ParametresService parametres, RechercheService recherche, AnnuaireService annuaire,
                            Clock horloge) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.reponseIaRepository = reponseIaRepository;
        this.vigie = vigie;
        this.acces = acces;
        this.parametres = parametres;
        this.recherche = recherche;
        this.annuaire = annuaire;
        this.horloge = horloge;
    }

    @Transactional
    public ConversationResponse creer(Long coursId, CurrentUser user) {
        acces.contexte(user, coursId);
        exigerVigieActive(coursId);
        ConversationVigie c = new ConversationVigie();
        c.setUserId(user.id());
        c.setCoursId(coursId);
        conversationRepository.save(c);
        return vue(c, user);
    }

    public List<ConversationResponse> lister(CurrentUser user) {
        acces.exigerConnecte(user);
        return conversationRepository.findByUserIdOrderByDerniereActiviteLeDesc(user.id()).stream()
                .map(c -> new ConversationResponse(c.getId(), c.getCoursId(), titreCours(c.getCoursId()), c.getTitre(),
                        c.getCreeLe(), c.getDerniereActiviteLe(), List.of()))
                .toList();
    }

    public ConversationResponse lire(Long id, CurrentUser user) {
        return vue(mienne(id, user), user);
    }

    @Transactional
    public MessageVigieResponse envoyer(Long id, MessageVigieRequest req, CurrentUser user) {
        ConversationVigie c = mienne(id, user);
        acces.contexte(user, c.getCoursId());
        exigerVigieActive(c.getCoursId());
        if (messageRepository.compter(user.id(), RoleMessage.APPRENANT, LocalDateTime.now(horloge).minusHours(1))
                >= QUESTIONS_PAR_HEURE) {
            throw new TropDeRequetesException("Tu as posé beaucoup de questions à La Vigie cette heure-ci. "
                    + "Réessaie un peu plus tard.");
        }
        boolean examen = vigie.modeExamen(c.getCoursId());
        ModeVigie mode = examen && req.mode() == ModeVigie.DIRECTE ? ModeVigie.GUIDEE : req.mode();
        if (mode == ModeVigie.GENERAL) {
            String refus = vigie.generaleAutorisee(c.getCoursId());
            if (refus != null) {
                throw new ForbiddenException(refus);
            }
        }
        List<ReformulerIaDto.Message> historique = historique(c.getId());
        enregistrer(c, RoleMessage.APPRENANT, req.texte().trim(), null);

        long debut = System.currentTimeMillis();
        ReponseIa r;
        if (mode == ModeVigie.GENERAL) {
            r = vigie.generer(c.getCoursId(), req.texte().trim());
        } else {
            Production p = vigie.produire(c.getCoursId(), req.texte().trim(), historique, mode);
            r = new ReponseIa();
            r.setCoursId(c.getCoursId());
            r.setMode(mode);
            vigie.appliquer(r, p, System.currentTimeMillis() - debut);
        }
        r.setConversationId(c.getId());
        reponseIaRepository.save(r);
        MessageVigie m = enregistrer(c, RoleMessage.VIGIE, r.getTexte(), r.getId());
        if (c.getTitre() == null) {
            c.setTitre(Textes.extrait(req.texte(), 120));
        }
        c.setDerniereActiviteLe(LocalDateTime.now(horloge));
        return new MessageVigieResponse(m.getId(), m.getRole(), m.getTexte(), vigie.vue(r, user), m.getCreeLe());
    }

    /** Brouillon de question à partir de la dernière question posée (titre, contenu, questions similaires). */
    public BrouillonQuestionResponse brouillon(Long id, CurrentUser user) {
        ConversationVigie c = mienne(id, user);
        List<MessageVigie> messages = messageRepository.findByConversationIdOrderByCreeLeAscIdAsc(c.getId());
        String question = messages.stream().filter(m -> m.getRole() == RoleMessage.APPRENANT)
                .reduce((a, b) -> b).map(MessageVigie::getTexte)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune question dans cette conversation"));
        String titre = Textes.extrait(question, 150);
        if (titre.length() < 10) {
            titre = "Question sur le cours : " + titre;
        }
        String contenu = question + "\n\n_Question préparée avec l'assistant La Vigie._";
        return new BrouillonQuestionResponse(c.getCoursId(), c.getId(), titre, contenu, List.of(),
                recherche.similaires(c.getCoursId(), titre, null).similaires());
    }

    /**
     * Publication d'une question depuis l'assistant : le fil reçoit une copie de la dernière réponse sourcée de
     * La Vigie (si la conversation appartient à l'auteur et porte sur le même cours). Rien sinon.
     */
    @Transactional
    public void attacher(Discussion d, Long conversationId, CurrentUser user) {
        conversationRepository.findById(conversationId)
                .filter(c -> c.getUserId().equals(user.id()) && c.getCoursId().equals(d.getCoursId()))
                .flatMap(c -> messageRepository.findByConversationIdOrderByCreeLeAscIdAsc(c.getId()).stream()
                        .filter(m -> m.getRole() == RoleMessage.VIGIE && m.getReponseIaId() != null)
                        .map(m -> reponseIaRepository.findById(m.getReponseIaId()).orElse(null))
                        .filter(r -> r != null && r.getNiveau() != NiveauReponseIa.GENERAL)
                        .reduce((a, b) -> b))
                .ifPresent(source -> {
                    ReponseIa copie = new ReponseIa();
                    copie.setDiscussionId(d.getId());
                    copie.setCoursId(d.getCoursId());
                    copie.setNiveau(source.getNiveau());
                    copie.setMode(source.getMode());
                    copie.setStatut(source.getStatut());
                    copie.setTexte(source.getTexte());
                    copie.setSourcesJson(source.getSourcesJson());
                    copie.setIndicesJson(source.getIndicesJson());
                    copie.setConfiance(source.getConfiance());
                    copie.setVersionPrompt(source.getVersionPrompt());
                    copie.setFournisseur(source.getFournisseur());
                    copie.setJetonsEstimes(0);
                    reponseIaRepository.save(copie);
                });
    }

    // ------------------------------------------------------------------ Aides

    private ConversationVigie mienne(Long id, CurrentUser user) {
        acces.exigerConnecte(user);
        return conversationRepository.findById(id).filter(c -> c.getUserId().equals(user.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
    }

    private void exigerVigieActive(Long coursId) {
        if (!parametres.cours(coursId).isVigieActive()) {
            throw new ForbiddenException("La Vigie est désactivée pour ce cours");
        }
    }

    private List<ReformulerIaDto.Message> historique(Long conversationId) {
        List<MessageVigie> messages = messageRepository.findByConversationIdOrderByCreeLeAscIdAsc(conversationId);
        return messages.subList(Math.max(0, messages.size() - 2 * ECHANGES_GARDES), messages.size()).stream()
                .map(m -> new ReformulerIaDto.Message(m.getRole().name(), m.getTexte())).toList();
    }

    private MessageVigie enregistrer(ConversationVigie c, RoleMessage role, String texte, Long reponseIaId) {
        MessageVigie m = new MessageVigie();
        m.setConversationId(c.getId());
        m.setRole(role);
        m.setTexte(texte == null ? "" : texte);
        m.setReponseIaId(reponseIaId);
        return messageRepository.save(m);
    }

    private ConversationResponse vue(ConversationVigie c, CurrentUser user) {
        List<MessageVigie> messages = messageRepository.findByConversationIdOrderByCreeLeAscIdAsc(c.getId());
        Map<Long, ReponseIa> reponses = reponseIaRepository.findAllById(messages.stream()
                        .map(MessageVigie::getReponseIaId).filter(x -> x != null).toList()).stream()
                .collect(Collectors.toMap(ReponseIa::getId, r -> r));
        List<MessageVigieResponse> vues = messages.stream().map(m -> new MessageVigieResponse(m.getId(), m.getRole(),
                m.getTexte(), m.getReponseIaId() == null ? null : vueReponse(reponses.get(m.getReponseIaId()), user),
                m.getCreeLe())).toList();
        return new ConversationResponse(c.getId(), c.getCoursId(), titreCours(c.getCoursId()), c.getTitre(),
                c.getCreeLe(), c.getDerniereActiviteLe(), vues);
    }

    private VigieResponse vueReponse(ReponseIa r, CurrentUser user) {
        return r == null ? null : vigie.vue(r, user);
    }

    private String titreCours(Long coursId) {
        return annuaire.cours(coursId).map(AnnuaireService.CoursInfo::titre).orElse("Cours n° " + coursId);
    }

}

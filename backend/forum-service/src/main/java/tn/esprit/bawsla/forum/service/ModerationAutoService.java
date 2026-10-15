package tn.esprit.bawsla.forum.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tn.esprit.bawsla.forum.client.ModererIaDto;
import tn.esprit.bawsla.forum.dto.VerificationRequest;
import tn.esprit.bawsla.forum.dto.VerificationResponse;
import tn.esprit.bawsla.forum.entity.Commentaire;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.Signalement;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.CategorieModeration;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.MotifSignalement;
import tn.esprit.bawsla.forum.entity.enums.TypeNotification;
import tn.esprit.bawsla.forum.evenement.CommentaireCree;
import tn.esprit.bawsla.forum.evenement.DiscussionCreee;
import tn.esprit.bawsla.forum.evenement.MessageMasque;
import tn.esprit.bawsla.forum.evenement.ReponseCreee;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.SignalementRepository;
import tn.esprit.bawsla.forum.service.AnnuaireService.CoursInfo;

/**
 * Modération automatique (plan §4.5), en tâche de fond après chaque publication (question, réponse, commentaire) :
 * <ol>
 *   <li>mots interdits réglés par l'admin (sans IA) ;</li>
 *   <li>sinon l'ai-service (règles sans LLM puis LLM) donne une catégorie et un score ;</li>
 *   <li>score ≥ seuil de masquage (0,85) : message masqué, signalement de l'IA dans la file, auteur prévenu ;
 *       seuil de signalement (0,5) ≤ score : publié mais signalé au formateur du cours.</li>
 * </ol>
 * Si l'IA est indisponible, le message reste publié (les signalements des membres restent possibles).
 * Avant publication, {@link #verifier} signale à l'auteur les données personnelles de son message.
 */
@Service
public class ModerationAutoService {

    private static final Logger log = LoggerFactory.getLogger(ModerationAutoService.class);
    static final String ROLE_IA = "IA";
    static final double SCORE_MOTS_INTERDITS = 0.9;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern TELEPHONE = Pattern.compile("(?<![\\w])(?:\\+?\\d[\\s.-]?){7,14}\\d(?![\\w])");

    /** Texte d'un message à analyser. */
    record Message(CibleType type, Long id, Long discussionId, Long coursId, Long auteurId, String titre,
                   String contenu, boolean masque) {
    }

    /** Verdict retenu (mots interdits ou IA). */
    record Verdict(CategorieModeration categorie, double score, String raison) {
    }

    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final CommentaireRepository commentaireRepository;
    private final SignalementRepository signalementRepository;
    private final IaService ia;
    private final ParametresService parametres;
    private final AnnuaireService annuaire;
    private final NotificationService notifications;
    private final JournalService journal;
    private final ApresValidation apresValidation;
    private final ApplicationEventPublisher evenements;
    private final TransactionTemplate transaction;

    public ModerationAutoService(DiscussionRepository discussionRepository, ReponseRepository reponseRepository,
                                 CommentaireRepository commentaireRepository,
                                 SignalementRepository signalementRepository, IaService ia,
                                 ParametresService parametres, AnnuaireService annuaire,
                                 NotificationService notifications, JournalService journal,
                                 ApresValidation apresValidation, ApplicationEventPublisher evenements,
                                 PlatformTransactionManager transactions) {
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.commentaireRepository = commentaireRepository;
        this.signalementRepository = signalementRepository;
        this.ia = ia;
        this.parametres = parametres;
        this.annuaire = annuaire;
        this.notifications = notifications;
        this.journal = journal;
        this.apresValidation = apresValidation;
        this.evenements = evenements;
        this.transaction = Transactions.nouvelle(transactions);
    }

    // ------------------------------------------------------------------ Déclencheurs

    @EventListener
    public void discussionCreee(DiscussionCreee e) {
        apresValidation.lancer(() -> analyser(CibleType.DISCUSSION, e.discussionId()));
    }

    @EventListener
    public void reponseCreee(ReponseCreee e) {
        apresValidation.lancer(() -> analyser(CibleType.REPONSE, e.reponseId()));
    }

    @EventListener
    public void commentaireCree(CommentaireCree e) {
        apresValidation.lancer(() -> analyser(CibleType.COMMENTAIRE, e.commentaireId()));
    }

    // ------------------------------------------------------------------ Analyse

    /** Analyse un message publié ; ne lève jamais (tâche de fond). */
    void analyser(CibleType type, Long id) {
        try {
            Message m = transaction.execute(s -> charger(type, id).orElse(null));
            if (m == null || m.masque()) {
                return;
            }
            Verdict v = motsInterdits(m).orElseGet(() -> ia.moderer(m.titre(), m.contenu())
                    .map(ModerationAutoService::verdict).orElse(null));
            if (v == null || v.categorie() == CategorieModeration.OK
                    || v.score() < parametres.decimal(ParametresService.SEUIL_SIGNALEMENT)) {
                return;
            }
            boolean masquer = v.score() >= parametres.decimal(ParametresService.SEUIL_MASQUAGE);
            transaction.executeWithoutResult(s -> decider(m, v, masquer));
        } catch (RuntimeException e) {
            log.warn("Modération automatique de {} {} impossible : {}", type, id, e.getMessage());
        }
    }

    private void decider(Message m, Verdict v, boolean masquer) {
        Signalement s = new Signalement();
        s.setCibleType(m.type());
        s.setCibleId(m.id());
        s.setCoursId(m.coursId());
        s.setAuteurCibleId(m.auteurId());
        s.setMotif(motif(v.categorie()));
        s.setCategorieIa(v.categorie());
        s.setScoreIa(Math.round(v.score() * 1000) / 1000.0);
        s.setRaisonIa(Textes.extrait(v.raison(), 290));
        signalementRepository.save(s);
        if (masquer && masquer(m)) {
            journal.tracer(JournalService.SYSTEME, ROLE_IA, ActionModeration.MASQUAGE_AUTO, m.type().name(), m.id(),
                    m.coursId(), v.categorie() + " (" + s.getScoreIa() + ") : " + Textes.extrait(v.raison(), 200));
            evenements.publishEvent(new MessageMasque(m.type(), m.id(), m.discussionId(), m.coursId(), m.auteurId(),
                    true, true));
        }
        annuaire.cours(m.coursId()).map(CoursInfo::enseignantId).ifPresent(formateur -> notifications.notifier(
                formateur, TypeNotification.SIGNALEMENT, m.discussionId(),
                m.type() == CibleType.REPONSE ? m.id() : null, null,
                (masquer ? "Masqué automatiquement : " : "À vérifier : ") + libelle(v.categorie())));
    }

    private boolean masquer(Message m) {
        switch (m.type()) {
            case DISCUSSION -> discussionRepository.findById(m.id()).ifPresent(d -> d.setMasquee(true));
            case REPONSE -> reponseRepository.findById(m.id()).ifPresent(r -> {
                r.setMasquee(true);
                if (r.isAcceptee()) {
                    r.setAcceptee(false);
                    discussionRepository.findById(r.getDiscussionId()).ifPresent(d -> {
                        d.setReponseAccepteeId(null);
                        FilService.recalculerStatut(d);
                    });
                }
            });
            case COMMENTAIRE -> commentaireRepository.findById(m.id()).ifPresent(c -> c.setMasque(true));
            default -> {
                return false;
            }
        }
        return true;
    }

    Optional<Message> charger(CibleType type, Long id) {
        return switch (type) {
            case DISCUSSION -> discussionRepository.findById(id).filter(d -> d.getSupprimeeLe() == null)
                    .map(d -> new Message(type, id, id, d.getCoursId(), d.getAuteurId(), d.getTitre(), d.getContenu(),
                            d.isMasquee()));
            case REPONSE -> reponseRepository.findById(id).filter(r -> r.getSupprimeeLe() == null)
                    .flatMap(r -> discussionRepository.findById(r.getDiscussionId()).map(d -> new Message(type, id,
                            d.getId(), d.getCoursId(), r.getAuteurId(), null, r.getContenu(), r.isMasquee())));
            case COMMENTAIRE -> commentaireRepository.findById(id).filter(c -> c.getSupprimeLe() == null)
                    .flatMap(c -> discussionRepository.findById(c.getDiscussionId()).map(d -> new Message(type, id,
                            d.getId(), d.getCoursId(), c.getAuteurId(), null, c.getContenu(), c.isMasque())));
            default -> Optional.empty();
        };
    }

    // ------------------------------------------------------------------ Règles locales

    /** Mots interdits réglés par l'admin (liste séparée par des virgules, sans tenir compte des accents). */
    Optional<Verdict> motsInterdits(Message m) {
        String texte = normaliser((m.titre() == null ? "" : m.titre()) + " " + m.contenu());
        return Arrays.stream(parametres.texte(ParametresService.MOTS_INTERDITS).split(","))
                .map(ModerationAutoService::normaliser).map(String::trim).filter(mot -> mot.length() >= 2)
                .filter(mot -> Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(mot) + "(?![\\p{L}\\p{N}])")
                        .matcher(texte).find())
                .findFirst()
                .map(mot -> new Verdict(CategorieModeration.IRRESPECTUEUX, SCORE_MOTS_INTERDITS,
                        "Mot interdit par la modération"));
    }

    /** Avant publication : données personnelles du message (sans IA), à signaler à l'auteur. */
    public VerificationResponse verifier(VerificationRequest req) {
        String texte = (req.titre() == null ? "" : req.titre()) + "\n" + (req.contenu() == null ? "" : req.contenu());
        List<String> alertes = new ArrayList<>();
        if (TELEPHONE.matcher(texte).find()) {
            alertes.add("Ton message contient un numéro de téléphone, veux-tu le retirer ?");
        }
        if (EMAIL.matcher(texte).find()) {
            alertes.add("Ton message contient une adresse email, veux-tu la retirer ?");
        }
        return new VerificationResponse(!alertes.isEmpty(), alertes);
    }

    // ------------------------------------------------------------------ Aides

    static Verdict verdict(ModererIaDto.Reponse r) {
        CategorieModeration categorie;
        try {
            categorie = CategorieModeration.valueOf(r.categorie());
        } catch (IllegalArgumentException e) {
            categorie = CategorieModeration.OK;
        }
        String raison = r.raison() == null ? "" : r.raison();
        if (r.regle() != null) {
            raison = raison + " (règle " + r.regle() + ")";
        }
        return new Verdict(categorie, Math.max(0, Math.min(1, r.score())), raison);
    }

    static MotifSignalement motif(CategorieModeration c) {
        return switch (c) {
            case HORS_SUJET -> MotifSignalement.HORS_SUJET;
            case IRRESPECTUEUX -> MotifSignalement.IRRESPECTUEUX;
            case SPAM -> MotifSignalement.SPAM;
            case FUITE_EXAMEN -> MotifSignalement.FUITE_EXAMEN;
            default -> MotifSignalement.AUTRE;
        };
    }

    static String libelle(CategorieModeration c) {
        return switch (c) {
            case HORS_SUJET -> "hors sujet";
            case IRRESPECTUEUX -> "message irrespectueux";
            case SPAM -> "spam";
            case FUITE_EXAMEN -> "partage de réponses d'examen";
            case INJECTION -> "tentative de manipulation de La Vigie";
            case DONNEES_PERSONNELLES -> "données personnelles exposées";
            default -> "à vérifier";
        };
    }

    static String normaliser(String texte) {
        return Normalizer.normalize(texte == null ? "" : texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}

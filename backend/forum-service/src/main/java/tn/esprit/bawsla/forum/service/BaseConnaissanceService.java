package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.PassageForum;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.evenement.ReponseAcceptee;
import tn.esprit.bawsla.forum.evenement.ReponseApprouvee;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.PassageForumRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.service.IaService.Vecteurs;

/**
 * Base de connaissances du forum (plan §4.9), deuxième source de La Vigie : chaque réponse acceptée ou approuvée
 * par le formateur, et chaque réponse de La Vigie validée ou corrigée, devient un passage vectorisé.
 * Seul un passage encore validé par un humain est utilisé (vérifié à la lecture, voir PassageForumRepository).
 */
@Service
@Transactional
public class BaseConnaissanceService {

    static final int LONGUEUR_PASSAGE = 1500;

    private final PassageForumRepository passageRepository;
    private final ReponseRepository reponseRepository;
    private final DiscussionRepository discussionRepository;
    private final IaService ia;
    private final ApresValidation apresValidation;
    private final TransactionTemplate transaction;
    private final Clock horloge;

    public BaseConnaissanceService(PassageForumRepository passageRepository, ReponseRepository reponseRepository,
                                   DiscussionRepository discussionRepository, IaService ia,
                                   ApresValidation apresValidation, PlatformTransactionManager transactions,
                                   Clock horloge) {
        this.passageRepository = passageRepository;
        this.reponseRepository = reponseRepository;
        this.discussionRepository = discussionRepository;
        this.ia = ia;
        this.apresValidation = apresValidation;
        this.transaction = Transactions.nouvelle(transactions);
        this.horloge = horloge;
    }

    @EventListener
    public void reponseAcceptee(ReponseAcceptee e) {
        if (e.reponseId() != null) {
            ajouterReponse(e.reponseId());
        }
    }

    @EventListener
    public void reponseApprouvee(ReponseApprouvee e) {
        if (e.approuvee()) {
            ajouterReponse(e.reponseId());
        }
    }

    public void ajouterReponse(Long reponseId) {
        if (passageRepository.existsByReponseId(reponseId)) {
            return;
        }
        Reponse r = reponseRepository.findById(reponseId).orElse(null);
        if (r == null) {
            return;
        }
        Discussion d = discussionRepository.findById(r.getDiscussionId()).orElse(null);
        if (d == null) {
            return;
        }
        enregistrer(d, reponseId, null, d.getTitre() + "\n" + Textes.extrait(r.getContenu(), LONGUEUR_PASSAGE));
    }

    /** Réponse de La Vigie validée (texte d'origine) ou corrigée (texte du formateur). */
    public void ajouterVigie(ReponseIa r) {
        if (r.getDiscussionId() == null || passageRepository.existsByReponseIaId(r.getId())) {
            return;
        }
        discussionRepository.findById(r.getDiscussionId()).ifPresent(d -> {
            String texte = r.getTexteCorrige() != null ? r.getTexteCorrige() : r.getTexte();
            enregistrer(d, null, r.getId(), d.getTitre() + "\n" + Textes.extrait(texte, LONGUEUR_PASSAGE));
        });
    }

    private void enregistrer(Discussion d, Long reponseId, Long reponseIaId, String texte) {
        PassageForum p = new PassageForum();
        p.setCoursId(d.getCoursId());
        p.setDiscussionId(d.getId());
        p.setReponseId(reponseId);
        p.setReponseIaId(reponseIaId);
        p.setTexte(texte);
        p.setValideLe(LocalDateTime.now(horloge));
        passageRepository.save(p);
        Long discussionId = d.getId();
        apresValidation.lancer(() -> vectoriser(discussionId));
    }

    /** Vectorise les passages du forum d'un fil qui n'ont pas encore de vecteur (tâche de fond). */
    void vectoriser(Long discussionId) {
        List<PassageForum> aFaire = transaction.execute(
                s -> passageRepository.findByEmbeddingIsNullAndDiscussionId(discussionId));
        if (aFaire == null || aFaire.isEmpty()) {
            return;
        }
        ia.vectoriserDocuments(aFaire.stream().map(PassageForum::getTexte).toList()).ifPresent((Vecteurs v) ->
                transaction.executeWithoutResult(s -> {
                    for (int i = 0; i < aFaire.size(); i++) {
                        int n = i;
                        passageRepository.findById(aFaire.get(i).getId()).ifPresent(p -> {
                            p.setEmbedding(v.valeurs().get(n));
                            p.setModeleEmbedding(v.modele());
                        });
                    }
                }));
    }
}

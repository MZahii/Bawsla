package tn.esprit.bawsla.forum.service;

import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ForbiddenException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.ReactionsResponse;
import tn.esprit.bawsla.forum.dto.VoteRequest;
import tn.esprit.bawsla.forum.dto.VoteResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Reaction;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.Reputation;
import tn.esprit.bawsla.forum.entity.Vote;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.TypeReaction;
import tn.esprit.bawsla.forum.evenement.VoteModifie;
import tn.esprit.bawsla.forum.repository.ReactionRepository;
import tn.esprit.bawsla.forum.repository.ReputationRepository;
import tn.esprit.bawsla.forum.repository.VoteRepository;
import tn.esprit.bawsla.forum.service.FilService.Fil;
import tn.esprit.bawsla.forum.service.FilService.FilReponse;

/**
 * Votes +1 / −1 sur questions et réponses (les « likes » du cahier des charges) et réactions sur les réponses.
 * Un vote par personne et par cible, annulable (valeur 0), jamais sur son propre message ;
 * le vote négatif demande 50 points de réputation (réglable par l'admin).
 */
@Service
@Transactional(readOnly = true)
public class VoteService {

    private final VoteRepository voteRepository;
    private final ReactionRepository reactionRepository;
    private final ReputationRepository reputationRepository;
    private final FilService fils;
    private final AccesService acces;
    private final ParametresService parametres;
    private final PresentationService presentation;
    private final ApplicationEventPublisher evenements;

    public VoteService(VoteRepository voteRepository, ReactionRepository reactionRepository,
                       ReputationRepository reputationRepository, FilService fils, AccesService acces,
                       ParametresService parametres, PresentationService presentation,
                       ApplicationEventPublisher evenements) {
        this.voteRepository = voteRepository;
        this.reactionRepository = reactionRepository;
        this.reputationRepository = reputationRepository;
        this.fils = fils;
        this.acces = acces;
        this.parametres = parametres;
        this.presentation = presentation;
        this.evenements = evenements;
    }

    @Transactional
    public VoteResponse voter(VoteRequest req, CurrentUser user) {
        if (req.cibleType() == CibleType.COMMENTAIRE) {
            throw new BadRequestException("On ne vote pas sur un commentaire");
        }
        Long auteurCible;
        Discussion discussion;
        Reponse reponse = null;
        if (req.cibleType() == CibleType.DISCUSSION) {
            Fil fil = fils.charger(req.cibleId(), user);
            discussion = fil.discussion();
            auteurCible = discussion.getAuteurId();
        } else {
            FilReponse fil = fils.chargerReponse(req.cibleId(), user);
            discussion = fil.discussion();
            reponse = fil.reponse();
            auteurCible = reponse.getAuteurId();
        }
        if (auteurCible.equals(user.id())) {
            throw new ForbiddenException("Tu ne peux pas voter pour ton propre message");
        }
        acces.exigerSansSanction(user);

        Optional<Vote> existant = voteRepository.findByUserIdAndCibleTypeAndCibleId(user.id(), req.cibleType(),
                req.cibleId());
        int ancienne = existant.map(Vote::getValeur).orElse(0);
        int nouvelle = req.valeur();
        if (nouvelle == -1 && ancienne != -1) {
            int minimum = parametres.entier(ParametresService.POINTS_VOTE_NEGATIF);
            int points = reputationRepository.findById(user.id()).map(Reputation::getPoints).orElse(0);
            if (points < minimum) {
                throw new ForbiddenException("Le vote négatif demande au moins " + minimum + " points de réputation");
            }
        }
        if (nouvelle == ancienne) {
            return new VoteResponse(req.cibleType(), req.cibleId(), score(discussion, reponse), nouvelle);
        }
        if (nouvelle == 0) {
            voteRepository.delete(existant.orElseThrow());
        } else {
            Vote vote = existant.orElseGet(() -> {
                Vote v = new Vote();
                v.setUserId(user.id());
                v.setCibleType(req.cibleType());
                v.setCibleId(req.cibleId());
                return v;
            });
            vote.setValeur(nouvelle);
            voteRepository.save(vote);
        }
        int delta = nouvelle - ancienne;
        if (reponse != null) {
            reponse.setScore(reponse.getScore() + delta);
        } else {
            discussion.setScore(discussion.getScore() + delta);
        }
        evenements.publishEvent(new VoteModifie(req.cibleType(), req.cibleId(), discussion.getId(),
                discussion.getCoursId(), auteurCible, user.id(), ancienne, nouvelle));
        return new VoteResponse(req.cibleType(), req.cibleId(), score(discussion, reponse), nouvelle);
    }

    /** Ajoute (ajouter = true) ou retire une réaction ; idempotent. */
    @Transactional
    public ReactionsResponse reagir(Long reponseId, TypeReaction type, boolean ajouter, CurrentUser user) {
        FilReponse fil = fils.chargerReponse(reponseId, user);
        if (fil.reponse().getAuteurId().equals(user.id())) {
            throw new ForbiddenException("Tu ne peux pas réagir à ta propre réponse");
        }
        acces.exigerSansSanction(user);
        Optional<Reaction> existante = reactionRepository.findByUserIdAndReponseIdAndType(user.id(), reponseId, type);
        if (ajouter && existante.isEmpty()) {
            Reaction r = new Reaction();
            r.setUserId(user.id());
            r.setReponseId(reponseId);
            r.setType(type);
            reactionRepository.save(r);
        } else if (!ajouter) {
            existante.ifPresent(reactionRepository::delete);
        }
        reactionRepository.flush();
        return presentation.reactions(reponseId, user);
    }

    private static int score(Discussion d, Reponse r) {
        return r != null ? r.getScore() : d.getScore();
    }
}

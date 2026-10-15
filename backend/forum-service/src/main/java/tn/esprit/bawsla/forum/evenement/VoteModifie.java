package tn.esprit.bawsla.forum.evenement;

import tn.esprit.bawsla.forum.entity.enums.CibleType;

/** Vote posé, changé ou annulé (ancienne ou nouvelle valeur 0 = absence de vote). */
public record VoteModifie(CibleType cibleType,
                          Long cibleId,
                          Long discussionId,
                          Long coursId,
                          Long auteurCibleId,
                          Long votantId,
                          int ancienneValeur,
                          int nouvelleValeur) {
}

package tn.esprit.bawsla.forum.evenement;

import tn.esprit.bawsla.forum.entity.enums.CibleType;

/** Message masqué (ou rétabli) par la modération ; automatique = décision de l'IA. */
public record MessageMasque(CibleType cibleType,
                            Long cibleId,
                            Long discussionId,
                            Long coursId,
                            Long auteurId,
                            boolean masque,
                            boolean automatique) {
}

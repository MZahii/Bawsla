package tn.esprit.bawsla.forum.evenement;

import java.time.LocalDateTime;

/** Nouvelle réponse humaine ; questionSansReponseDepuis = date de la question si elle n'avait aucune réponse (badge Secouriste). */
public record ReponseCreee(Long reponseId,
                           Long discussionId,
                           Long coursId,
                           Long auteurId,
                           Long auteurDiscussionId,
                           boolean anonyme,
                           String contenu,
                           LocalDateTime questionSansReponseDepuis) {
}

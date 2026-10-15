package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;

/** Carte d'un fil dans la liste (extrait sur 2 lignes, pastilles, compteurs). */
public record DiscussionResume(Long id, Long coursId, String coursTitre, Long chapitreId, Integer page,
                               TypeDiscussion type, String titre, String extrait, StatutDiscussion statut,
                               boolean epinglee, boolean verrouillee, boolean faq, int score, int nbReponses,
                               int nbVues, List<String> etiquettes, AuteurResponse auteur,
                               AuteurResponse dernierActeur, LocalDateTime derniereActiviteLe,
                               LocalDateTime creeLe, boolean nonLue, boolean approuvee, boolean vigie,
                               Double pertinence) {
}

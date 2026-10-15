package tn.esprit.bawsla.cours.dto;

import java.util.List;

/**
 * Pages du cours à suivre côté studio : celles lues par l'IA (vision) et celles qui n'ont pas de texte. {@code total} : toutes les pages du
 * cours ; {@code aLire} : pages que la lecture par vision peut encore traiter ; {@code plafond} : pages lues par lancement (les suivantes
 * partent au lancement suivant).
 */
public record PagesLuesResponse(int total, int aLire, int plafond, List<PageLueResponse> pages) {
}

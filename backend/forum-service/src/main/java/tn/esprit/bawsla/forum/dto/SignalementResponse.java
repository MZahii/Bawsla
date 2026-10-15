package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.enums.CategorieModeration;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.MotifSignalement;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;

/** Élément de la file de modération ; signalePar absent = signalement automatique de l'IA. */
public record SignalementResponse(Long id, CibleType cibleType, Long cibleId, Long coursId, String coursTitre,
                                  Long discussionId, String extrait, AuteurResponse auteurCible,
                                  AuteurResponse signalePar, MotifSignalement motif, String commentaire,
                                  CategorieModeration categorieIa, Double scoreIa, String raisonIa,
                                  StatutSignalement statut, boolean escalade, boolean cibleMasquee,
                                  String decision, LocalDateTime decideLe, LocalDateTime creeLe) {
}

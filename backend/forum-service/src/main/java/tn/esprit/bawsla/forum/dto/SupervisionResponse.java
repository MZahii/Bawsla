package tn.esprit.bawsla.forum.dto;

import java.util.List;
import java.util.Map;

import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;

/** Supervision de La Vigie (coût, fiabilité, sécurité). */
public record SupervisionResponse(long appels, long jetonsEstimes, Map<String, Long> parFournisseur,
                                  Map<NiveauReponseIa, Long> parNiveau, double tauxAbstention, double tauxUtile,
                                  long injectionsBloquees, long donneesPersonnellesMasquees,
                                  String versionPromptActive, List<SerieStat> appelsParJour,
                                  List<SerieStat> sujetsGeneraux) {
}

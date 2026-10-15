package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** Vue d'ensemble de la plateforme. */
public record AdminStatistiquesResponse(StatistiquesResponse global, long signalementsOuverts,
                                        List<CoursActivite> coursActifs, List<CoursActivite> coursSansReponse) {
}

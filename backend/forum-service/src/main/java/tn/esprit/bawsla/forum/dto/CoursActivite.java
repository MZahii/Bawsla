package tn.esprit.bawsla.forum.dto;

/** Activité du forum d'un cours (classements de la vue d'ensemble admin). */
public record CoursActivite(Long coursId, String coursTitre, Long enseignantId, long questions, long sansReponse,
                            double tauxResolution) {
}

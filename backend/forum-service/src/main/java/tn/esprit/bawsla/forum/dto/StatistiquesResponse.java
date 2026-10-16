package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** Indicateurs du forum (cours, formateur ou plateforme). */
public record StatistiquesResponse(long questions, long sansReponse, Double delaiPremiereReponseHeures,
                                   double tauxResolution, double tauxCouvertureVigie, double tauxVigieUtile,
                                   List<SerieStat> parChapitre, List<SerieStat> parSemaine) {
}

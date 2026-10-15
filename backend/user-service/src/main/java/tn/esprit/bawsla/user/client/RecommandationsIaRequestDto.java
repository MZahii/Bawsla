package tn.esprit.bawsla.user.client;

import java.util.List;

/** Contrat 5 : cours suivis, domaines, cours en difficulté (notions ratées) et vrai catalogue ; aucun identifiant. */
public record RecommandationsIaRequestDto(List<Long> coursSuivis, List<String> domaines, List<Long> coursEnDifficulte,
                                          List<CoursCatalogue> coursDisponibles, int nombre) {

    public record CoursCatalogue(Long coursId, String titre, String categorie, String niveau) {
    }
}

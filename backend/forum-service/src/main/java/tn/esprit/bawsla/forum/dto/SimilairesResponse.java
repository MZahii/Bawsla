package tn.esprit.bawsla.forum.dto;

import java.util.List;

/**
 * Résultat de la recherche de similaires. semantique = false quand le fournisseur d'embeddings est indisponible
 * (recherche par mots-clés seulement). doublonId : discussion résolue au-dessus du seuil « doublon ».
 */
public record SimilairesResponse(List<SimilaireResponse> similaires, boolean semantique, Long doublonId) {
}

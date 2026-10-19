package tn.esprit.bawsla.user.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Texte généré par l'IA (conseil, plan, explication du risque, rapport de la classe). {@code contenu} est la réponse
 * d'ai-service telle quelle ; {@code depuisCache} vaut true quand les données n'ont pas changé depuis la dernière
 * génération (aucun nouvel appel au LLM). {@code dateGeneration} porte son fuseau : le navigateur l'affiche à
 * l'heure locale.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContenuIaResponse(String type, JsonNode contenu, String fournisseur, OffsetDateTime dateGeneration,
                                boolean depuisCache) {
}

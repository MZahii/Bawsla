package tn.esprit.bawsla.forum.dto;

import java.util.Map;

import jakarta.validation.constraints.NotEmpty;

/** Réglages globaux (clé / valeur) ; les clés inconnues sont refusées. */
public record ParametresForumRequest(@NotEmpty(message = "Aucun réglage fourni") Map<String, String> valeurs) {
}

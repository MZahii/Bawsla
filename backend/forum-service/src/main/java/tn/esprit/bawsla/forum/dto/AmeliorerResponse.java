package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** Suggestions que l'utilisateur accepte ou ignore une par une. */
public record AmeliorerResponse(String titre, List<String> manques, List<String> etiquettes) {
}

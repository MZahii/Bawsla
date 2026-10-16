package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** Groupe de questions proches (K-Means sur les embeddings), nommé par le LLM. */
public record ThemeTendance(String nom, int taille, double part, List<Long> discussionIds, List<String> exemples) {
}

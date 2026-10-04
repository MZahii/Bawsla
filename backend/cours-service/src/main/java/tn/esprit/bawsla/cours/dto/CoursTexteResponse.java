package tn.esprit.bawsla.cours.dto;

/**
 * Contrat GET /api/cours/{id}/texte : texte extrait du PDF, consommé par le RAG du Forum.
 * stub = true tant que l'extraction PDF n'est pas implémentée.
 */
public record CoursTexteResponse(Long coursId, String texte, boolean stub) {
}

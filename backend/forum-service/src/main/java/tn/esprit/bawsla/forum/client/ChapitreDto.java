package tn.esprit.bawsla.forum.client;

/** Chapitre d'un cours (GET /api/cours/{id}/chapitres, lu s'il existe ; sinon ignoré). */
public record ChapitreDto(Long id, String titre, String contenu, Integer ordre) {
}

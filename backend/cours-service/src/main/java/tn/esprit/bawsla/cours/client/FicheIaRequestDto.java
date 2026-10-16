package tn.esprit.bawsla.cours.client;

import tn.esprit.bawsla.cours.entity.Niveau;

/** Corps de POST /api/ai/cours/fiche. Le texte est celui du cours (emails et téléphones déjà masqués). */
public record FicheIaRequestDto(Long coursId, String texte, Niveau niveau) {
}

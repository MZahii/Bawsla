package tn.esprit.bawsla.forum.dto;

import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;

/**
 * Source citée par La Vigie, numérotée [n] dans le texte. origine COURS : chapitre / page du cours et lien
 * « Ouvrir dans le cours » ; origine FORUM : réponse validée dans le fil discussionId.
 */
public record SourceVigie(int numero, NiveauReponseIa origine, Long coursId, String cours, Long chapitreId,
                          String chapitre, Integer page, String passage, String citation, Long discussionId,
                          String lien) {
}

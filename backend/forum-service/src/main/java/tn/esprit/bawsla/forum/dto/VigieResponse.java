package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

import tn.esprit.bawsla.forum.entity.enums.ModeVigie;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;

/**
 * Réponse de La Vigie (fil ou assistant privé). confiance et niveauConfiance (ELEVEE, MOYENNE, FAIBLE) sont
 * absents pour une réponse générale (niveau GENERAL) : une confiance auto-déclarée n'est jamais affichée.
 * avertissement : par exemple « Le cours n'est pas joignable : réponse tirée du forum uniquement ».
 */
public record VigieResponse(Long id, Long discussionId, Long conversationId, NiveauReponseIa niveau,
                            StatutReponseIa statut, ModeVigie mode, String texte, String texteCorrige,
                            List<SourceVigie> sources, List<String> indices, List<String> incertitudes,
                            List<String> aVerifier, Integer confiance, String niveauConfiance,
                            boolean verifieeParFormateur, int utileOui, int utileNon, Boolean monAvis,
                            List<SimilaireResponse> questionsSimilaires, String avertissement,
                            String versionPrompt, String fournisseur, LocalDateTime creeLe) {
}

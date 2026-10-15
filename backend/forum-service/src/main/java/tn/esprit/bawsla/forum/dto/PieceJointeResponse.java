package tn.esprit.bawsla.forum.dto;

/** Image jointe ; url relative à la gateway (/api/forum/pieces-jointes/{id}). */
public record PieceJointeResponse(Long id, String url, String type, long taille) {
}

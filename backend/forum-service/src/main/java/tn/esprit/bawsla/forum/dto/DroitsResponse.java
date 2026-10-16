package tn.esprit.bawsla.forum.dto;

/** Actions permises à l'utilisateur courant (confort d'affichage ; le backend revérifie tout). */
public record DroitsResponse(boolean modifier, boolean supprimer, boolean repondre, boolean accepter,
                             boolean moderer) {
}

package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** Historique forum d'un membre (vue admin). */
public record HistoriqueMembreResponse(AuteurResponse membre, long messagesMasques,
                                       List<SignalementResponse> signalementsRecus,
                                       List<SanctionResponse> sanctions, ReputationResponse reputation) {
}

package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.MotifSignalement;

public record SignalementRequest(@NotNull(message = "La cible est obligatoire") CibleType cibleType,
                                 @NotNull(message = "La cible est obligatoire") Long cibleId,
                                 @NotNull(message = "Le motif est obligatoire") MotifSignalement motif,
                                 @Size(max = 500, message = "Le commentaire fait au plus 500 caractères")
                                 String commentaire) {
}

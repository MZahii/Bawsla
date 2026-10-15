package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.enums.TypeSanction;

public record SanctionResponse(Long id, AuteurResponse membre, TypeSanction type, String motif,
                               LocalDateTime dateDebut, LocalDateTime dateFin, boolean active, Long decidePar,
                               Long signalementId, LocalDateTime leveeLe) {
}

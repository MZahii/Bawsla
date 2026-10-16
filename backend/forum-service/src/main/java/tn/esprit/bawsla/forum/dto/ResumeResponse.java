package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Encadré « En bref » d'un fil long. */
public record ResumeResponse(Long discussionId, List<String> puces, int nbMessages, String versionPrompt,
                             LocalDateTime creeLe) {
}

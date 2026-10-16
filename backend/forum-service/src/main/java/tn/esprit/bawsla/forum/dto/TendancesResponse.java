package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Onglet « Tendances » du studio (mis en cache 1 h). */
public record TendancesResponse(List<ThemeTendance> themes, List<SerieStat> parChapitre,
                                List<DiscussionResume> suggestionsFaq, List<SerieStat> demandesGenerales,
                                LocalDateTime calculeLe) {
}

package tn.esprit.bawsla.forum.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.entity.JournalModeration;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.repository.JournalModerationRepository;

/** Trace chaque décision de modération (qui, quoi, quand, pourquoi) dans le journal non modifiable. */
@Service
public class JournalService {

    /** Acteur « système » des décisions automatiques (masquage par l'IA). */
    public static final long SYSTEME = 0L;

    private final JournalModerationRepository repository;

    public JournalService(JournalModerationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void tracer(CurrentUser acteur, ActionModeration action, String cibleType, Long cibleId, Long coursId,
                       String motif) {
        tracer(acteur.id(), acteur.role(), action, cibleType, cibleId, coursId, motif);
    }

    @Transactional
    public void tracer(Long acteurId, String role, ActionModeration action, String cibleType, Long cibleId,
                       Long coursId, String motif) {
        JournalModeration j = new JournalModeration();
        j.setActeurId(acteurId);
        j.setRole(role);
        j.setAction(action);
        j.setCibleType(cibleType);
        j.setCibleId(cibleId);
        j.setCoursId(coursId);
        j.setMotif(motif == null || motif.isBlank() ? null : motif.trim());
        repository.save(j);
    }
}

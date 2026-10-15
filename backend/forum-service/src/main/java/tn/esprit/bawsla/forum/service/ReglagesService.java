package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.JournalResponse;
import tn.esprit.bawsla.forum.dto.PageResponse;
import tn.esprit.bawsla.forum.dto.ParametresCoursRequest;
import tn.esprit.bawsla.forum.dto.ParametresCoursResponse;
import tn.esprit.bawsla.forum.entity.JournalModeration;
import tn.esprit.bawsla.forum.entity.ParametresForumCours;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.repository.JournalModerationRepository;
import tn.esprit.bawsla.forum.repository.ParametresForumCoursRepository;

/**
 * Réglages et journal :
 * <ul>
 *   <li>forum d'un cours (formateur du cours ou admin) : ouvert, anonymat, La Vigie, réponse générale, mode examen
 *       jusqu'à une date, modèle de question ;</li>
 *   <li>réglages globaux (admin) : seuils de modération et de similarité, limites anti-flood, mots interdits ;</li>
 *   <li>journal de modération (admin) : filtres et export CSV.</li>
 * </ul>
 * Chaque modification est tracée.
 */
@Service
@Transactional(readOnly = true)
public class ReglagesService {

    static final int TAILLE_JOURNAL = 50;
    static final int MAX_EXPORT = 5000;

    private final ParametresService parametres;
    private final ParametresForumCoursRepository coursRepository;
    private final JournalModerationRepository journalRepository;
    private final AccesService acces;
    private final JournalService journal;
    private final Clock horloge;

    public ReglagesService(ParametresService parametres, ParametresForumCoursRepository coursRepository,
                           JournalModerationRepository journalRepository, AccesService acces, JournalService journal,
                           Clock horloge) {
        this.parametres = parametres;
        this.coursRepository = coursRepository;
        this.journalRepository = journalRepository;
        this.acces = acces;
        this.journal = journal;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ Forum d'un cours

    public ParametresCoursResponse cours(Long coursId, CurrentUser user) {
        acces.exigerModerateur(acces.contexte(user, coursId));
        return ParametresCoursResponse.from(parametres.cours(coursId));
    }

    @Transactional
    public ParametresCoursResponse modifierCours(Long coursId, ParametresCoursRequest req, CurrentUser user) {
        acces.exigerModerateur(acces.contexte(user, coursId));
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        if (req.modeExamen() && req.modeExamenFin() != null && !req.modeExamenFin().isAfter(maintenant)) {
            throw new BadRequestException("La fin du mode examen doit être dans le futur");
        }
        ParametresForumCours p = parametres.cours(coursId);
        p.setOuvert(req.ouvert());
        p.setAnonymatAutorise(req.anonymatAutorise());
        p.setVigieActive(req.vigieActive());
        p.setReponseGeneraleAutorisee(req.reponseGeneraleAutorisee());
        p.setModeExamen(req.modeExamen());
        p.setModeExamenFin(req.modeExamen() ? req.modeExamenFin() : null);
        p.setQuestionModele(req.questionModele() == null || req.questionModele().isBlank() ? null
                : req.questionModele().trim());
        p.setModifiePar(user.id());
        p.setDateModification(maintenant);
        coursRepository.save(p);
        journal.tracer(user, ActionModeration.MODIFIER_PARAMETRES, "COURS", coursId, coursId, resume(p));
        return ParametresCoursResponse.from(p);
    }

    // ------------------------------------------------------------------ Réglages globaux (admin)

    public Map<String, String> globaux(CurrentUser user) {
        acces.exigerAdmin(user);
        return parametres.tous();
    }

    @Transactional
    public Map<String, String> modifierGlobaux(Map<String, String> valeurs, CurrentUser user) {
        acces.exigerAdmin(user);
        Map<String, String> resultat = parametres.modifier(valeurs, user.id());
        journal.tracer(user, ActionModeration.MODIFIER_PARAMETRES, "PLATEFORME", null, null,
                String.join(", ", valeurs.keySet()));
        return resultat;
    }

    // ------------------------------------------------------------------ Journal (admin)

    public PageResponse<JournalResponse> journal(Long acteurId, ActionModeration action, LocalDate du, LocalDate au,
                                                 Integer page, CurrentUser user) {
        acces.exigerAdmin(user);
        int numero = page == null ? 0 : Math.max(0, page);
        Page<JournalModeration> r = rechercher(acteurId, action, du, au, PageRequest.of(numero, TAILLE_JOURNAL));
        return PageResponse.of(r.getContent().stream().map(JournalResponse::from).toList(), numero, TAILLE_JOURNAL,
                r.getTotalElements());
    }

    /** Export CSV (séparateur « ; », UTF-8 avec BOM pour Excel), au plus 5000 lignes. */
    public String journalCsv(Long acteurId, ActionModeration action, LocalDate du, LocalDate au, CurrentUser user) {
        acces.exigerAdmin(user);
        List<JournalModeration> lignes = rechercher(acteurId, action, du, au, PageRequest.of(0, MAX_EXPORT))
                .getContent();
        StringBuilder csv = new StringBuilder("﻿date;acteur;role;action;cible;cible_id;cours_id;motif\n");
        for (JournalModeration j : lignes) {
            csv.append(j.getCreeLe()).append(';').append(j.getActeurId()).append(';').append(j.getRole()).append(';')
                    .append(j.getAction()).append(';').append(j.getCibleType()).append(';')
                    .append(j.getCibleId() == null ? "" : j.getCibleId()).append(';')
                    .append(j.getCoursId() == null ? "" : j.getCoursId()).append(';')
                    .append(champ(j.getMotif())).append('\n');
        }
        return csv.toString();
    }

    private Page<JournalModeration> rechercher(Long acteurId, ActionModeration action, LocalDate du, LocalDate au,
                                               PageRequest page) {
        return journalRepository.rechercher(acteurId, action, du == null ? null : du.atStartOfDay(),
                au == null ? null : au.plusDays(1).atStartOfDay(), page);
    }

    /** Champ CSV : guillemets doublés, entouré de guillemets, sans formule interprétable par un tableur. */
    static String champ(String valeur) {
        if (valeur == null) {
            return "";
        }
        String v = valeur.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String resume(ParametresForumCours p) {
        return "ouvert=" + p.isOuvert() + ", anonymat=" + p.isAnonymatAutorise() + ", vigie=" + p.isVigieActive()
                + ", general=" + p.isReponseGeneraleAutorisee() + ", examen=" + p.isModeExamen()
                + (p.getModeExamenFin() == null ? "" : " jusqu'au " + p.getModeExamenFin());
    }
}

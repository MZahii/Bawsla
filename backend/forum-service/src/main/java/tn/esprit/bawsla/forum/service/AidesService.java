package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.client.AidesIaDto;
import tn.esprit.bawsla.forum.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.AmeliorerRequest;
import tn.esprit.bawsla.forum.dto.AmeliorerResponse;
import tn.esprit.bawsla.forum.dto.EtiquetteResponse;
import tn.esprit.bawsla.forum.dto.ResumeResponse;
import tn.esprit.bawsla.forum.entity.Commentaire;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.ResumeFil;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;
import tn.esprit.bawsla.forum.repository.CommentaireRepository;
import tn.esprit.bawsla.forum.repository.ReponseIaRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.ResumeFilRepository;
import tn.esprit.bawsla.forum.service.FilService.Fil;

/**
 * Aides de La Vigie autour des fils :
 * <ul>
 *   <li>« En bref » (plan §4.4) : pour un fil de plus de 6 messages, 3 à 5 puces tirées du texte du fil seul,
 *       gardées en base et régénérées quand le nombre de messages change. Sans IA, l'ancien résumé reste affiché
 *       (ou aucun). Rôles seulement, jamais de nom ; messages masqués exclus ; La Vigie exclue en mode examen.</li>
 *   <li>« Améliorer ma question » (plan §4.6) : titre plus clair, ce qui manque, étiquettes déjà utilisées.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AidesService {

    static final int SEUIL_MESSAGES = 6;
    static final int MESSAGES_MAX = 60;
    static final String SEPARATEUR = "\n";

    private final ResumeFilRepository resumeRepository;
    private final ReponseRepository reponseRepository;
    private final CommentaireRepository commentaireRepository;
    private final ReponseIaRepository reponseIaRepository;
    private final FilService fils;
    private final AccesService acces;
    private final EtiquetteService etiquettes;
    private final ParametresService parametres;
    private final IaService ia;
    private final Clock horloge;

    public AidesService(ResumeFilRepository resumeRepository, ReponseRepository reponseRepository,
                        CommentaireRepository commentaireRepository, ReponseIaRepository reponseIaRepository,
                        FilService fils, AccesService acces, EtiquetteService etiquettes,
                        ParametresService parametres, IaService ia, Clock horloge) {
        this.resumeRepository = resumeRepository;
        this.reponseRepository = reponseRepository;
        this.commentaireRepository = commentaireRepository;
        this.reponseIaRepository = reponseIaRepository;
        this.fils = fils;
        this.acces = acces;
        this.etiquettes = etiquettes;
        this.parametres = parametres;
        this.ia = ia;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ En bref

    /** Résumé du fil, ou null si le fil est court ou si aucun résumé n'a pu être produit. */
    @Transactional
    public ResumeResponse resume(Long discussionId, CurrentUser user) {
        Fil fil = fils.charger(discussionId, user);
        Discussion d = fil.discussion();
        List<AidesIaDto.MessageFil> messages = messages(d);
        Optional<ResumeFil> existant = resumeRepository.findById(discussionId);
        if (messages.size() <= SEUIL_MESSAGES) {
            return null;
        }
        if (existant.isPresent() && existant.get().getNbMessagesAuMoment() == messages.size()) {
            return vue(existant.get());
        }
        Optional<AidesIaDto.ResumerReponse> nouveau = ia.resumer(new AidesIaDto.ResumerRequete(d.getTitre(),
                messages.subList(Math.max(0, messages.size() - MESSAGES_MAX), messages.size())));
        if (nouveau.isEmpty() || nouveau.get().puces() == null || nouveau.get().puces().isEmpty()) {
            return existant.map(this::vue).orElse(null);
        }
        ResumeFil r = existant.orElseGet(ResumeFil::new);
        r.setDiscussionId(discussionId);
        r.setTexte(String.join(SEPARATEUR, nouveau.get().puces()));
        r.setNbMessagesAuMoment(messages.size());
        r.setVersionPrompt(nouveau.get().versionPrompt());
        r.setCreeLe(LocalDateTime.now(horloge));
        return vue(resumeRepository.save(r));
    }

    /** Messages visibles du fil, dans l'ordre, avec leur rôle (jamais l'auteur). */
    List<AidesIaDto.MessageFil> messages(Discussion d) {
        List<AidesIaDto.MessageFil> messages = new ArrayList<>();
        messages.add(new AidesIaDto.MessageFil("QUESTION", d.getContenu()));
        boolean examen = parametres.modeExamenActif(parametres.cours(d.getCoursId()));
        if (!examen) {
            reponseIaRepository.principales(d.getId(), NiveauReponseIa.GENERAL).stream().findFirst()
                    .filter(r -> r.getStatut() == StatutReponseIa.GENEREE || r.getStatut() == StatutReponseIa.VALIDEE
                            || r.getStatut() == StatutReponseIa.CORRIGEE)
                    .ifPresent(r -> messages.add(new AidesIaDto.MessageFil("VIGIE", texte(r))));
        }
        List<Reponse> reponses = reponseRepository.findByDiscussionIdAndSupprimeeLeIsNull(d.getId()).stream()
                .filter(r -> !r.isMasquee()).sorted((a, b) -> a.getCreeLe().compareTo(b.getCreeLe())).toList();
        for (Reponse r : reponses) {
            String marque = r.isAcceptee() ? "[ACCEPTÉE] " : r.getApprouveeParId() != null ? "[APPROUVÉE] " : "";
            messages.add(new AidesIaDto.MessageFil("REPONSE", marque + r.getContenu()));
        }
        for (Commentaire c : commentaireRepository.findByDiscussionIdAndSupprimeLeIsNullOrderByCreeLeAsc(d.getId())) {
            if (!c.isMasque()) {
                messages.add(new AidesIaDto.MessageFil("COMMENTAIRE", c.getContenu()));
            }
        }
        return messages;
    }

    private static String texte(ReponseIa r) {
        return r.getTexteCorrige() != null ? r.getTexteCorrige() : r.getTexte();
    }

    private ResumeResponse vue(ResumeFil r) {
        return new ResumeResponse(r.getDiscussionId(), Arrays.asList(r.getTexte().split(SEPARATEUR)),
                r.getNbMessagesAuMoment(), r.getVersionPrompt(), r.getCreeLe());
    }

    // ------------------------------------------------------------------ Améliorer ma question

    public AmeliorerResponse ameliorer(AmeliorerRequest req, CurrentUser user) {
        acces.contexte(user, req.coursId());
        List<String> existantes = etiquettes.rechercher(req.coursId(), null, user).stream()
                .map(EtiquetteResponse::libelle).toList();
        AidesIaDto.AmeliorerReponse r = ia.ameliorer(new AidesIaDto.AmeliorerRequete(req.titre(),
                        req.contenu() == null ? "" : req.contenu(), existantes))
                .orElseThrow(() -> new ServiceUnavailableException(
                        "L'aide à la rédaction est indisponible pour le moment"));
        return new AmeliorerResponse(r.titre(), r.manques() == null ? List.of() : r.manques(),
                r.etiquettes() == null ? List.of() : r.etiquettes());
    }
}

package tn.esprit.bawsla.cours.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import tn.esprit.bawsla.cours.client.AudioIaRequestDto;
import tn.esprit.bawsla.cours.client.PageIaDto;
import tn.esprit.bawsla.cours.client.ResumeLocalIaDto;
import tn.esprit.bawsla.cours.client.ResumeLocalIaRequestDto;
import tn.esprit.bawsla.cours.common.exception.BawslaException;
import tn.esprit.bawsla.cours.config.AsyncConfig;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.service.IaGateway.AudioIa;
import tn.esprit.bawsla.cours.service.ResumeLocalEnregistrement.PourAudio;

/**
 * Traitement du résumé automatique local, en arrière-plan : lecture du cours (TEXTE), résumé (RESUME), audio (AUDIO). Le
 * {@code Authorization} de l'utilisateur est capturé par le contrôleur AVANT le passage en asynchrone et reçu ici en paramètre.
 * L'audio utilise la chaîne vocale existante avec la langue DÉTECTÉE par l'IA (voix française pour un cours français, anglaise sinon).
 */
@Component
public class ResumeLocalTraitement {

    private static final Logger log = LoggerFactory.getLogger(ResumeLocalTraitement.class);

    private final IaGateway ia;
    private final FicheEnregistrement suivi;
    private final ResumeLocalEnregistrement enregistrement;

    public ResumeLocalTraitement(IaGateway ia, FicheEnregistrement suivi, ResumeLocalEnregistrement enregistrement) {
        this.ia = ia;
        this.suivi = suivi;
        this.enregistrement = enregistrement;
    }

    @Async(AsyncConfig.IA_EXECUTOR)
    public void generer(String execution, Long coursId, List<PageIaDto> pages, String longueur, String mode, String authorization) {
        EtapeIA courante = EtapeIA.TEXTE;
        try {
            suivi.passer(execution, coursId, EtapeIA.TEXTE, StatutTraitement.EN_COURS, null);
            suivi.passer(execution, coursId, EtapeIA.TEXTE, StatutTraitement.TERMINE, null);

            courante = EtapeIA.RESUME;
            suivi.passer(execution, coursId, EtapeIA.RESUME, StatutTraitement.EN_COURS, null);
            ResumeLocalIaDto dto = ia.resumeLocal(authorization, new ResumeLocalIaRequestDto(coursId, pages, longueur, mode));
            Long resumeId = enregistrement.enregistrer(coursId, dto);
            suivi.passer(execution, coursId, EtapeIA.RESUME, StatutTraitement.TERMINE, null);

            courante = EtapeIA.AUDIO;
            audio(execution, coursId, resumeId, authorization);
        } catch (RuntimeException e) {
            echec(execution, coursId, courante, e);
            suivi.annulerRestant(execution, coursId);
        }
    }

    /** Audio seul (à la validation, si le résumé n'en a pas encore). */
    @Async(AsyncConfig.IA_EXECUTOR)
    public void produireAudio(String execution, Long coursId, Long resumeId, String authorization) {
        try {
            audio(execution, coursId, resumeId, authorization);
        } catch (RuntimeException e) {
            echec(execution, coursId, EtapeIA.AUDIO, e);
        }
    }

    private void audio(String execution, Long coursId, Long resumeId, String authorization) {
        suivi.passer(execution, coursId, EtapeIA.AUDIO, StatutTraitement.EN_COURS, null);
        try {
            PourAudio texte = enregistrement.preparerAudio(resumeId);
            AudioIa son = ia.audio(authorization, new AudioIaRequestDto(texte.texte(), texte.langue()));
            enregistrement.enregistrerAudio(resumeId, son);
            suivi.passer(execution, coursId, EtapeIA.AUDIO, StatutTraitement.TERMINE, null);
        } catch (RuntimeException e) {
            // Le résumé est déjà enregistré : l'échec de l'audio ne le remet pas en cause.
            echec(execution, coursId, EtapeIA.AUDIO, e);
        }
    }

    private void echec(String execution, Long coursId, EtapeIA etape, RuntimeException e) {
        String message = e instanceof BawslaException ? e.getMessage() : "Erreur inattendue pendant le traitement.";
        log.warn("Résumé local du cours {} en échec à l'étape {} : {}", coursId, etape, e.getClass().getSimpleName());
        try {
            suivi.passer(execution, coursId, etape, StatutTraitement.ECHEC, message);
        } catch (RuntimeException ignore) {
            // Le suivi lui-même est indisponible : rien de plus à faire, l'étape restera « en cours » jusqu'au redémarrage.
        }
    }
}

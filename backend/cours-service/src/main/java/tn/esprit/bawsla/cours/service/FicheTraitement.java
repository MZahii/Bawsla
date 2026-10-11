package tn.esprit.bawsla.cours.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import tn.esprit.bawsla.cours.client.AudioIaRequestDto;
import tn.esprit.bawsla.cours.client.FicheIaDto;
import tn.esprit.bawsla.cours.client.FicheIaRequestDto;
import tn.esprit.bawsla.cours.common.exception.BawslaException;
import tn.esprit.bawsla.cours.config.AsyncConfig;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.entity.StatutTraitement;

/**
 * Traitement IA en arrière-plan : lecture du texte, fiche de révision, puis audio du résumé.
 * Aucune transaction n'est ouverte pendant l'attente de l'IA (chaque écriture passe par {@link FicheEnregistrement}).
 * L'identité de l'utilisateur n'est pas dans ce thread : on garde seulement son {@code Authorization}, que la gateway
 * revalide à chaque appel à l'IA.
 */
@Component
public class FicheTraitement {

    private static final Logger log = LoggerFactory.getLogger(FicheTraitement.class);

    private final IaGateway ia;
    private final FicheEnregistrement enregistrement;

    public FicheTraitement(IaGateway ia, FicheEnregistrement enregistrement) {
        this.ia = ia;
        this.enregistrement = enregistrement;
    }

    /** Génération complète : TEXTE, FICHE puis AUDIO. Un échec de la fiche annule l'audio ; l'inverse non. */
    @Async(AsyncConfig.IA_EXECUTOR)
    public void genererFiche(String execution, Long coursId, String texte, Niveau niveau, String authorization) {
        EtapeIA courante = EtapeIA.TEXTE;
        try {
            enregistrement.passer(execution, coursId, EtapeIA.TEXTE, StatutTraitement.EN_COURS, null);
            enregistrement.passer(execution, coursId, EtapeIA.TEXTE, StatutTraitement.TERMINE, null);

            courante = EtapeIA.FICHE;
            enregistrement.passer(execution, coursId, EtapeIA.FICHE, StatutTraitement.EN_COURS, null);
            FicheIaDto fiche = ia.fiche(authorization, new FicheIaRequestDto(coursId, texte, niveau));
            Long ficheId = enregistrement.enregistrerFiche(coursId, fiche);
            enregistrement.passer(execution, coursId, EtapeIA.FICHE, StatutTraitement.TERMINE, null);

            courante = EtapeIA.AUDIO;
            produireAudio(execution, coursId, ficheId, fiche.resume(), authorization);
        } catch (RuntimeException e) {
            echec(execution, coursId, courante, e);
            enregistrement.annulerRestant(execution, coursId);
        }
    }

    /** Audio seul (après validation d'une fiche corrigée à la main, dont le résumé n'a pas encore d'audio). */
    @Async(AsyncConfig.IA_EXECUTOR)
    public void genererAudio(String execution, Long coursId, Long ficheId, String resume, String authorization) {
        try {
            produireAudio(execution, coursId, ficheId, resume, authorization);
        } catch (RuntimeException e) {
            echec(execution, coursId, EtapeIA.AUDIO, e);
        }
    }

    private void produireAudio(String execution, Long coursId, Long ficheId, String resume, String authorization) {
        enregistrement.passer(execution, coursId, EtapeIA.AUDIO, StatutTraitement.EN_COURS, null);
        try {
            byte[] mp3 = ia.audio(authorization, new AudioIaRequestDto(resume));
            enregistrement.enregistrerAudio(coursId, ficheId, mp3);
            enregistrement.passer(execution, coursId, EtapeIA.AUDIO, StatutTraitement.TERMINE, null);
        } catch (RuntimeException e) {
            // La fiche est déjà enregistrée : l'échec de l'audio ne la remet pas en cause.
            echec(execution, coursId, EtapeIA.AUDIO, e);
        }
    }

    private void echec(String execution, Long coursId, EtapeIA etape, RuntimeException e) {
        String message = e instanceof BawslaException ? e.getMessage() : "Erreur inattendue pendant le traitement.";
        log.warn("Traitement IA du cours {} en échec à l'étape {} : {}", coursId, etape, e.getClass().getSimpleName());
        try {
            enregistrement.passer(execution, coursId, etape, StatutTraitement.ECHEC, message);
        } catch (RuntimeException ignore) {
            log.error("Impossible d'enregistrer l'échec du traitement du cours {}", coursId);
        }
    }

    /** Au démarrage : les traitements restés « en cours » ont été coupés par l'arrêt du service. */
    @EventListener(ApplicationReadyEvent.class)
    void recupererTraitementsInterrompus() {
        int n = enregistrement.marquerInterrompus();
        if (n > 0) {
            log.warn("{} étape(s) de traitement IA interrompue(s) par un redémarrage", n);
        }
    }
}

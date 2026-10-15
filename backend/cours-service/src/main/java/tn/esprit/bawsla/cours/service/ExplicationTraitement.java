package tn.esprit.bawsla.cours.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import tn.esprit.bawsla.cours.client.AudioExplicationIaDto;
import tn.esprit.bawsla.cours.client.AudioExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.ExplicationIaDto;
import tn.esprit.bawsla.cours.client.ExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.TraductionIaRequestDto;
import tn.esprit.bawsla.cours.common.exception.BawslaException;
import tn.esprit.bawsla.cours.config.AsyncConfig;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.LangueExplication;
import tn.esprit.bawsla.cours.entity.ModeExplication;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.service.ExplicationEnregistrement.PourAudio;

/**
 * Traitements IA de l'explication orale, en arrière-plan : script (ou traduction) puis, à la demande, audio d'aperçu.
 * Aucune identité n'est lisible dans ce thread : le {@code Authorization} de l'utilisateur est capturé par le contrôleur
 * AVANT le passage en asynchrone et reçu ici en paramètre ; la gateway le revalide à chaque appel à l'IA.
 * Aucune transaction n'est ouverte pendant l'attente de l'IA (écritures dans {@link ExplicationEnregistrement}).
 */
@Component
public class ExplicationTraitement {

    private static final Logger log = LoggerFactory.getLogger(ExplicationTraitement.class);

    /**
     * Tout ce qu'il faut pour générer un script. {@code script} : français, à partir du cours ; {@code traduction} : anglais,
     * à partir du français validé ({@code sourceId}). Un seul des deux est renseigné.
     */
    public record Tache(Long coursId, LangueExplication langue, ModeExplication mode, ExplicationIaRequestDto script,
                        TraductionIaRequestDto traduction, Long sourceId, boolean notionsChoisiesParIa) {
    }

    private final IaGateway ia;
    private final FicheEnregistrement suivi;
    private final ExplicationEnregistrement enregistrement;

    public ExplicationTraitement(IaGateway ia, FicheEnregistrement suivi, ExplicationEnregistrement enregistrement) {
        this.ia = ia;
        this.suivi = suivi;
        this.enregistrement = enregistrement;
    }

    /** Génération du script : TEXTE, puis SCRIPT (français) ou TRADUCTION (anglais). L'audio se demande ensuite avec l'aperçu. */
    @Async(AsyncConfig.IA_EXECUTOR)
    public void genererScript(String execution, Tache tache, String authorization) {
        EtapeIA courante = EtapeIA.TEXTE;
        try {
            suivi.passer(execution, tache.coursId(), EtapeIA.TEXTE, StatutTraitement.EN_COURS, null);
            suivi.passer(execution, tache.coursId(), EtapeIA.TEXTE, StatutTraitement.TERMINE, null);

            courante = tache.traduction() != null ? EtapeIA.TRADUCTION : EtapeIA.SCRIPT;
            suivi.passer(execution, tache.coursId(), courante, StatutTraitement.EN_COURS, null);
            ExplicationIaDto dto = tache.traduction() != null
                    ? ia.traduireExplication(authorization, tache.traduction())
                    : ia.explication(authorization, tache.script());
            enregistrement.enregistrerScript(tache.coursId(), tache.langue(), tache.mode(), tache.sourceId(),
                    tache.notionsChoisiesParIa(), dto);
            suivi.passer(execution, tache.coursId(), courante, StatutTraitement.TERMINE, null);
        } catch (RuntimeException e) {
            echec(execution, tache.coursId(), courante, e);
            suivi.annulerRestant(execution, tache.coursId());
        }
    }

    /** Met le script en voix (aperçu du formateur, ou audio refait à la validation si le texte a changé depuis l'aperçu). */
    @Async(AsyncConfig.IA_EXECUTOR)
    public void produireAudio(String execution, Long coursId, Long explicationId, String authorization) {
        try {
            suivi.passer(execution, coursId, EtapeIA.AUDIO, StatutTraitement.EN_COURS, null);
            PourAudio script = enregistrement.preparerAudio(explicationId);
            AudioExplicationIaDto audio = ia.audioExplication(authorization,
                    new AudioExplicationIaRequestDto(script.segments(), script.langue().code(), script.mode().code()));
            enregistrement.enregistrerAudio(explicationId, audio, script.empreinte());
            suivi.passer(execution, coursId, EtapeIA.AUDIO, StatutTraitement.TERMINE, null);
        } catch (RuntimeException e) {
            echec(execution, coursId, EtapeIA.AUDIO, e);
        }
    }

    private void echec(String execution, Long coursId, EtapeIA etape, RuntimeException e) {
        String message = e instanceof BawslaException ? e.getMessage() : "Erreur inattendue pendant le traitement.";
        log.warn("Traitement de l'explication du cours {} en échec à l'étape {} : {}", coursId, etape, e.getClass().getSimpleName());
        try {
            suivi.passer(execution, coursId, etape, StatutTraitement.ECHEC, message);
        } catch (RuntimeException ignore) {
            log.error("Impossible d'enregistrer l'échec du traitement de l'explication du cours {}", coursId);
        }
    }
}

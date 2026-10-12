package tn.esprit.bawsla.cours.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.client.ResumeLocalIaDto;
import tn.esprit.bawsla.cours.client.ResumeLocalIaDto.PhraseIaDto;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.entity.PhraseResumeLocal;
import tn.esprit.bawsla.cours.entity.ResumeLocal;
import tn.esprit.bawsla.cours.repository.ResumeLocalRepository;
import tn.esprit.bawsla.cours.service.IaGateway.AudioIa;

/** Écritures courtes du résumé local, chacune dans sa transaction (le traitement asynchrone n'en garde aucune ouverte pendant l'attente). */
@Service
public class ResumeLocalEnregistrement {

    private final ResumeLocalRepository repository;
    private final StockageService stockage;

    public ResumeLocalEnregistrement(ResumeLocalRepository repository, StockageService stockage) {
        this.repository = repository;
        this.stockage = stockage;
    }

    /** Enregistre le résumé renvoyé par l'IA (statut GENERE) ; les précédents restent dans l'historique. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enregistrer(Long coursId, ResumeLocalIaDto dto) {
        if (dto.phrases() == null || dto.phrases().isEmpty() || dto.resume() == null || dto.resume().isBlank()) {
            throw new ServiceUnavailableException("Le résumé local est vide.");
        }
        ResumeLocal r = new ResumeLocal();
        r.setCoursId(coursId);
        r.setLangue(dto.langue() == null ? "fr" : dto.langue());
        r.setModele(coupe(dto.modele(), 120));
        r.setModeUtilise(dto.modeUtilise() == null ? "extractif" : dto.modeUtilise());
        r.setTexte(dto.resume().strip());
        r.setScoreFidelite(dto.scoreFidelite());
        r.setScoreReformule(dto.scoreReformule());
        r.setDureeCalculMs(dto.dureeMs());
        if (dto.alertes() != null) {
            dto.alertes().stream().map(a -> coupe(a, 500)).forEach(r.getAlertes()::add);
        }
        int ordre = 1;
        for (PhraseIaDto p : dto.phrases()) {
            PhraseResumeLocal phrase = new PhraseResumeLocal();
            phrase.setResume(r);
            phrase.setOrdre(ordre++);
            phrase.setTexte(p.texte() == null ? "" : p.texte().strip());
            phrase.setExtrait(p.extraitSource());
            phrase.setPage(p.page());
            phrase.setJustifiee(p.justifiee() == null || p.justifiee());
            r.getPhrases().add(phrase);
        }
        return repository.save(r).getId();
    }

    /** Texte et langue à mettre en voix, lus au début de la tâche audio. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PourAudio preparerAudio(Long resumeId) {
        ResumeLocal r = repository.findById(resumeId).orElseThrow(() -> new ResourceNotFoundException("Résumé local", resumeId));
        return new PourAudio(r.getCoursId(), r.getTexte(), r.getLangue());
    }

    public record PourAudio(Long coursId, String texte, String langue) {
    }

    /** Stocke l'audio sous un nom UUID et le rattache au résumé ; l'ancien fichier éventuel est supprimé. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerAudio(Long resumeId, AudioIa son) {
        ResumeLocal r = repository.findById(resumeId).orElseThrow(() -> new ResourceNotFoundException("Résumé local", resumeId));
        boolean wav = son.mime().contains("wav");
        String ancien = r.getCheminAudio();
        r.setCheminAudio(stockage.sauvegarder(StockageService.Type.AUDIO, son.octets(), wav ? "wav" : "mp3"));
        r.setMime(wav ? "audio/wav" : "audio/mpeg");
        r.setTailleAudio((long) son.octets().length);
        r.setFournisseurAudio(son.fournisseur());
        r.setDureeAudio(son.dureeMs());
        stockage.supprimerApresValidation(StockageService.Type.AUDIO, ancien);
    }

    private static String coupe(String texte, int max) {
        if (texte == null) {
            return null;
        }
        String propre = texte.strip();
        return propre.length() <= max ? propre : propre.substring(0, max);
    }
}

package tn.esprit.bawsla.cours.service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.client.AudioExplicationIaDto;
import tn.esprit.bawsla.cours.client.ExplicationIaDto;
import tn.esprit.bawsla.cours.client.MinutageIaDto;
import tn.esprit.bawsla.cours.client.SegmentIaDto;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.entity.ExplicationAudio;
import tn.esprit.bawsla.cours.entity.LangueExplication;
import tn.esprit.bawsla.cours.entity.Locuteur;
import tn.esprit.bawsla.cours.entity.ModeExplication;
import tn.esprit.bawsla.cours.entity.SegmentExplication;
import tn.esprit.bawsla.cours.entity.TypeSegment;
import tn.esprit.bawsla.cours.repository.ExplicationAudioRepository;

/**
 * Écritures courtes de l'explication orale, chacune dans sa transaction (REQUIRES_NEW) : le traitement asynchrone n'ouvre
 * jamais de transaction pendant qu'il attend l'IA.
 */
@Service
public class ExplicationEnregistrement {

    private final ExplicationAudioRepository repository;
    private final StockageService stockage;

    public ExplicationEnregistrement(ExplicationAudioRepository repository, StockageService stockage) {
        this.repository = repository;
        this.stockage = stockage;
    }

    /** Ce qu'il faut pour mettre un script en voix, lu au début de la tâche (le script peut être corrigé pendant l'attente). */
    public record PourAudio(Long coursId, LangueExplication langue, ModeExplication mode, String empreinte, List<SegmentIaDto> segments) {
    }

    /** Enregistre le script renvoyé par l'IA (statut GENEREE). L'ancien reste dans l'historique. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enregistrerScript(Long coursId, LangueExplication langue, ModeExplication mode, Long sourceId,
                                  boolean notionsChoisiesParIa, ExplicationIaDto dto) {
        if (dto.segments() == null || dto.segments().isEmpty()) {
            throw new ServiceUnavailableException("L'IA a renvoyé un script vide.");
        }
        ExplicationAudio e = new ExplicationAudio();
        e.setCoursId(coursId);
        e.setLangue(langue);
        e.setMode(mode);
        e.setSourceId(sourceId);
        e.setNotionsChoisiesParIa(notionsChoisiesParIa);
        e.setScoreFidelite(dto.scoreFidelite());
        e.setVersionPrompt(dto.versionPrompt());
        e.setModele(dto.modele());
        e.setNbMots(dto.nbMots());
        e.setDureeEstimeeSecondes(dto.dureeEstimeeSecondes());
        if (dto.alertes() != null) {
            e.getAlertes().addAll(dto.alertes());
        }
        int ordre = 1;
        for (SegmentIaDto s : dto.segments()) {
            SegmentExplication segment = new SegmentExplication();
            segment.setExplication(e);
            segment.setOrdre(s.ordre() != null ? s.ordre() : ordre);
            segment.setType(TypeSegment.depuis(s.type()));
            segment.setLocuteur(Locuteur.depuis(s.locuteur()));
            segment.setTexte(s.texte() == null ? "" : s.texte().strip());
            segment.setNotion(s.notion() == null ? null : coupe(s.notion(), 200));
            segment.setExtraitSource(s.extraitSource());
            segment.setPage(s.page());
            segment.setJustifiee(s.justifiee() == null || s.justifiee());
            segment.setAnalogie(Boolean.TRUE.equals(s.analogie()));
            e.getSegments().add(segment);
            ordre++;
        }
        e.setEmpreinteScript(e.calculerEmpreinte());
        return repository.save(e).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PourAudio preparerAudio(Long explicationId) {
        ExplicationAudio e = repository.findById(explicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Explication", explicationId));
        List<SegmentIaDto> segments = e.getSegments().stream()
                .map(s -> new SegmentIaDto(s.getOrdre(), s.getType().code(), s.getLocuteur().code(), s.getTexte(), s.getNotion(),
                        null, null, null, null))
                .toList();
        return new PourAudio(e.getCoursId(), e.getLangue(), e.getMode(), e.getEmpreinteScript(), segments);
    }

    /**
     * Stocke l'audio produit (sous un nom UUID) et le minutage de chaque segment. {@code empreinte} est celle du script qui a été
     * mis en voix : si le formateur l'a modifié entre-temps, l'audio reste enregistré mais n'est plus « à jour ».
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerAudio(Long explicationId, AudioExplicationIaDto dto, String empreinte) {
        ExplicationAudio e = repository.findById(explicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Explication", explicationId));
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(dto.audioBase64());
        } catch (IllegalArgumentException ex) {
            throw new ServiceUnavailableException("L'audio reçu de l'IA est illisible.");
        }
        boolean wav = dto.mime() != null && dto.mime().contains("wav");
        String ancien = e.getAudioFichier();
        e.setAudioFichier(stockage.sauvegarder(StockageService.Type.AUDIO, octets, wav ? "wav" : "mp3"));
        e.setAudioMime(wav ? "audio/wav" : "audio/mpeg");
        e.setAudioFournisseur(dto.fournisseur());
        e.setAudioTaille((long) octets.length);
        e.setDureeMs(dto.dureeMs());
        e.setMinutageEstime(Boolean.TRUE.equals(dto.minutageEstime()));
        e.setEmpreinteAudio(empreinte);
        e.setDateAudio(LocalDateTime.now());
        stockage.supprimerApresValidation(StockageService.Type.AUDIO, ancien);

        Map<Integer, MinutageIaDto> minutage = new HashMap<>();
        if (dto.segments() != null) {
            dto.segments().forEach(m -> minutage.put(m.ordre(), m));
        }
        for (SegmentExplication s : e.getSegments()) {
            MinutageIaDto m = minutage.get(s.getOrdre());
            s.setDebutMs(m == null ? null : m.debutMs());
            s.setFinMs(m == null ? null : m.finMs());
        }
    }

    private static String coupe(String texte, int max) {
        String propre = texte.strip();
        return propre.length() <= max ? propre : propre.substring(0, max);
    }
}

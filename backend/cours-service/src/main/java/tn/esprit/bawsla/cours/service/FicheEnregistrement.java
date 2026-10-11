package tn.esprit.bawsla.cours.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.client.FicheIaDto;
import tn.esprit.bawsla.cours.client.NotionIaDto;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.entity.AudioRevision;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.FicheRevision;
import tn.esprit.bawsla.cours.entity.Notion;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.entity.TraitementIA;
import tn.esprit.bawsla.cours.repository.AudioRevisionRepository;
import tn.esprit.bawsla.cours.repository.FicheRevisionRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;

/**
 * Écritures courtes du traitement IA, chacune dans sa propre transaction (REQUIRES_NEW) : le traitement asynchrone
 * n'ouvre jamais de transaction pendant qu'il attend l'IA, et peut aussi être appelé après le commit d'une autre.
 */
@Service
public class FicheEnregistrement {

    private static final int ERREUR_MAX = 500;

    private final TraitementIARepository traitementRepository;
    private final FicheRevisionRepository ficheRepository;
    private final AudioRevisionRepository audioRepository;
    private final StockageService stockage;

    public FicheEnregistrement(TraitementIARepository traitementRepository, FicheRevisionRepository ficheRepository,
                               AudioRevisionRepository audioRepository, StockageService stockage) {
        this.traitementRepository = traitementRepository;
        this.ficheRepository = ficheRepository;
        this.audioRepository = audioRepository;
        this.stockage = stockage;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void passer(String execution, Long coursId, EtapeIA etape, StatutTraitement statut, String erreur) {
        traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, execution).stream()
                .filter(t -> t.getEtape() == etape)
                .findFirst()
                .ifPresent(t -> {
                    LocalDateTime maintenant = LocalDateTime.now();
                    t.setStatut(statut);
                    if (statut == StatutTraitement.EN_COURS) {
                        t.setDebut(maintenant);
                    } else if (statut == StatutTraitement.TERMINE || statut == StatutTraitement.ECHEC) {
                        t.setFin(maintenant);
                        if (t.getDebut() != null) {
                            t.setDureeMs(Duration.between(t.getDebut(), maintenant).toMillis());
                        }
                        t.setErreur(erreur == null ? null : tronquer(erreur));
                    }
                });
    }

    /** Les étapes qui n'ont pas encore commencé ne peuvent plus avoir lieu : elles passent à ANNULE. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void annulerRestant(String execution, Long coursId) {
        for (TraitementIA t : traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, execution)) {
            if (t.getStatut() == StatutTraitement.EN_ATTENTE) {
                t.setStatut(StatutTraitement.ANNULE);
            }
        }
    }

    /** Enregistre la fiche renvoyée par l'IA (statut GENEREE) ; l'ancienne reste dans l'historique. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enregistrerFiche(Long coursId, FicheIaDto dto) {
        if (dto.resume() == null || dto.resume().isBlank()) {
            throw new ServiceUnavailableException("L'IA a renvoyé une fiche vide.");
        }
        FicheRevision fiche = new FicheRevision();
        fiche.setCoursId(coursId);
        fiche.setResume(dto.resume().strip());
        fiche.setScoreFidelite(dto.scoreFidelite());
        fiche.setNiveau(dto.niveau());
        fiche.setVersionPrompt(dto.versionPrompt());
        fiche.setModele(dto.modele());
        if (dto.motsCles() != null) {
            dto.motsCles().stream().filter(Objects::nonNull).map(String::strip).filter(m -> !m.isEmpty())
                    .forEach(fiche.getMotsCles()::add);
        }
        if (dto.alertes() != null) {
            fiche.getAlertes().addAll(dto.alertes());
        }
        List<NotionIaDto> notions = dto.notions() == null ? List.of() : dto.notions();
        int ordre = 1;
        for (NotionIaDto n : notions) {
            Notion notion = new Notion();
            notion.setFiche(fiche);
            notion.setOrdre(ordre++);
            notion.setTitre(coupe(n.titre(), 200));
            notion.setDefinition(n.definition());
            notion.setExtraitSource(n.extraitSource());
            notion.setJustifiee(n.justifiee() == null || n.justifiee());
            fiche.getNotions().add(notion);
        }
        return ficheRepository.save(fiche).getId();
    }

    /** Stocke le MP3 sous un nom UUID et le rattache à la fiche. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerAudio(Long coursId, Long ficheId, byte[] mp3) {
        AudioRevision audio = new AudioRevision();
        audio.setCoursId(coursId);
        audio.setFicheId(ficheId);
        audio.setFichier(stockage.sauvegarder(StockageService.Type.AUDIO, mp3, "mp3"));
        audio.setTaille((long) mp3.length);
        audioRepository.save(audio);
    }

    /** Au démarrage : un traitement resté « en cours » a été interrompu par l'arrêt du service. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int marquerInterrompus() {
        List<TraitementIA> actifs = traitementRepository.findByStatutIn(
                List.of(StatutTraitement.EN_ATTENTE, StatutTraitement.EN_COURS));
        for (TraitementIA t : actifs) {
            t.setStatut(t.getStatut() == StatutTraitement.EN_ATTENTE ? StatutTraitement.ANNULE : StatutTraitement.ECHEC);
            t.setFin(LocalDateTime.now());
            t.setErreur("Interrompu par un redémarrage du service : relance la génération.");
        }
        return actifs.size();
    }

    static String tronquer(String message) {
        return message.length() <= ERREUR_MAX ? message : message.substring(0, ERREUR_MAX - 1) + "…";
    }

    private static String coupe(String texte, int max) {
        String propre = texte == null ? "" : texte.strip();
        return propre.length() <= max ? propre : propre.substring(0, max);
    }
}

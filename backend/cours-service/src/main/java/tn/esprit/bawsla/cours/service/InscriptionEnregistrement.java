package tn.esprit.bawsla.cours.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.entity.ChapitreTermine;
import tn.esprit.bawsla.cours.entity.Inscription;
import tn.esprit.bawsla.cours.repository.ChapitreTermineRepository;
import tn.esprit.bawsla.cours.repository.InscriptionRepository;

/**
 * Écritures des inscriptions, chacune dans sa propre transaction courte. {@link InscriptionService} n'ouvre aucune transaction : si deux
 * requêtes créent la même inscription (double clic), la contrainte d'unicité fait échouer la seconde ICI, avant que l'appelant ne relise
 * l'inscription existante avec une transaction neuve.
 */
@Service
public class InscriptionEnregistrement {

    private final InscriptionRepository inscriptionRepository;
    private final ChapitreTermineRepository termineRepository;

    public InscriptionEnregistrement(InscriptionRepository inscriptionRepository, ChapitreTermineRepository termineRepository) {
        this.inscriptionRepository = inscriptionRepository;
        this.termineRepository = termineRepository;
    }

    /** @throws org.springframework.dao.DataIntegrityViolationException si l'apprenant est déjà inscrit (inscription concurrente). */
    @Transactional
    public Inscription creer(Long etudiantId, Long coursId) {
        return inscriptionRepository.saveAndFlush(new Inscription(etudiantId, coursId));
    }

    /** Marque le chapitre terminé (sans doublon) et date l'activité. {@code total} : nombre de chapitres du cours. */
    @Transactional
    public void marquer(Long inscriptionId, Long chapitreId, int total) {
        Inscription inscription = inscriptionRepository.findById(inscriptionId).orElseThrow();
        if (!termineRepository.existsByInscriptionIdAndChapitreId(inscriptionId, chapitreId)) {
            termineRepository.saveAndFlush(new ChapitreTermine(inscription, chapitreId));
        }
        inscription.setDateDerniereActivite(LocalDateTime.now());
        synchroniser(inscription, total);
    }

    @Transactional
    public void annuler(Long inscriptionId, Long chapitreId, int total) {
        Inscription inscription = inscriptionRepository.findById(inscriptionId).orElseThrow();
        termineRepository.supprimer(inscriptionId, chapitreId);
        synchroniser(inscription, total);
    }

    /** Suppression définitive de l'inscription et de ses chapitres terminés. */
    @Transactional
    public void supprimer(Long inscriptionId) {
        termineRepository.supprimerDeLInscription(inscriptionId);
        inscriptionRepository.deleteById(inscriptionId);
    }

    private void synchroniser(Inscription inscription, int total) {
        int termines = termineRepository.compterParInscription(List.of(inscription.getId())).stream()
                .mapToInt(l -> ((Number) l[1]).intValue()).sum();
        inscription.setTermine(total > 0 && termines >= total);
    }
}

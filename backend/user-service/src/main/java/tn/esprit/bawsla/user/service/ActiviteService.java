package tn.esprit.bawsla.user.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.user.entity.ActiviteJour;
import tn.esprit.bawsla.user.repository.ActiviteJourRepository;

/** Jours d'activité des utilisateurs : indicateur « jours actifs sur 30 jours » du Machine Learning. */
@Service
public class ActiviteService {

    static final int FENETRE_JOURS = 30;

    private final ActiviteJourRepository repository;
    private final Clock clock;

    public ActiviteService(ActiviteJourRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Note que l'utilisateur est actif aujourd'hui (sans effet s'il l'est déjà). Transaction propre : un éventuel
     * doublon concurrent (deux connexions à la même seconde) n'annule pas l'opération appelante.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrer(Long userId) {
        LocalDate aujourdhui = LocalDate.now(clock);
        if (repository.existsByUserIdAndJour(userId, aujourdhui)) {
            return;
        }
        try {
            repository.save(new ActiviteJour(userId, aujourdhui));
        } catch (DataIntegrityViolationException dejaEnregistre) {
            // Une autre requête l'a fait entre-temps : le résultat est le même.
        }
    }

    /** Jours avec au moins une activité parmi les 30 derniers, aujourd'hui compris (0 à 30). */
    @Transactional(readOnly = true)
    public int joursActifs(Long userId) {
        LocalDate depuis = LocalDate.now(clock).minusDays(FENETRE_JOURS - 1L);
        return (int) Math.min(FENETRE_JOURS, repository.countByUserIdAndJourGreaterThanEqual(userId, depuis));
    }
}

package tn.esprit.bawsla.cours.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.repository.CoursRepository;

/**
 * Au démarrage, ramène les cours existants aux matières de la liste commune avec le module Quiz. Idempotent : une fois les cours
 * convertis, un nouveau passage ne trouve plus rien à faire.
 *
 * <ul>
 *   <li>Données, Back-end, Front-end, DevOps, Outils : « Informatique » ;</li>
 *   <li>une matière de la liste écrite autrement (forme Unicode décomposée, espaces autour) : remise au libellé exact ;</li>
 *   <li>tout autre ancien libellé : « Autre », avec l'ancien libellé comme précision (80 caractères au plus) ;</li>
 *   <li>un cours sans matière reste tel quel (rien à convertir) : il est signalé dans le log.</li>
 * </ul>
 */
@Component
public class MigrationMatieres {

    private static final Logger log = LoggerFactory.getLogger(MigrationMatieres.class);

    /** Bilan d'un passage. */
    public record Bilan(int versInformatique, int normalisees, int versAutre, int sansMatiere) {

        public int total() {
            return versInformatique + normalisees + versAutre;
        }
    }

    private final CoursRepository repository;

    public MigrationMatieres(CoursRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public Bilan migrer() {
        int versInformatique = repository.migrerVersInformatique(Matieres.INFORMATIQUE, List.copyOf(Matieres.ANCIENNES_INFORMATIQUE));
        int normalisees = 0;
        int versAutre = 0;
        List<String> exemples = new ArrayList<>();
        for (Cours c : repository.findByCategorieIsNotNullAndCategorieNotIn(Matieres.LISTE)) {
            String ancien = c.getCategorie();
            var canonique = Matieres.canonique(ancien);
            if (canonique.isPresent()) {
                c.setCategorie(canonique.get());
                if (!Matieres.AUTRE.equals(canonique.get())) {
                    c.setCategoriePrecision(null);
                }
                normalisees++;
            } else if (Matieres.ANCIENNES_INFORMATIQUE.contains(Matieres.nettoyer(ancien))) {
                c.setCategorie(Matieres.INFORMATIQUE);
                versInformatique++;
            } else {
                String precision = Matieres.precisionDepuisAncienLibelle(ancien);
                c.setCategorie(Matieres.AUTRE);
                c.setCategoriePrecision(precision);
                versAutre++;
                exemples.add("« " + ancien + " » -> « " + precision + " »");
            }
        }
        int sansMatiere = (int) repository.findAll().stream().filter(c -> Matieres.nettoyer(c.getCategorie()) == null).count();

        Bilan bilan = new Bilan(versInformatique, normalisees, versAutre, sansMatiere);
        if (bilan.total() > 0) {
            log.info("Migration des matières : {} cours vers « {} », {} remis au libellé exact, {} vers « {} » avec l'ancien libellé en précision {}",
                    versInformatique, Matieres.INFORMATIQUE, normalisees, versAutre, Matieres.AUTRE, exemples);
        }
        if (sansMatiere > 0) {
            log.warn("{} cours sans matière : le formateur doit en choisir une à la prochaine modification", sansMatiere);
        }
        return bilan;
    }
}

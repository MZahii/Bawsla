package tn.esprit.bawsla.cours.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.cours.entity.Commande;

public interface CommandeRepository extends JpaRepository<Commande, Long> {

    Optional<Commande> findByEtudiantIdAndCoursId(Long etudiantId, Long coursId);

    boolean existsByEtudiantIdAndCoursId(Long etudiantId, Long coursId);

    List<Commande> findByEtudiantIdOrderByDatePaiementDescIdDesc(Long etudiantId);

    List<Commande> findAllByOrderByDatePaiementDescIdDesc();

    List<Commande> findByCoursIdInOrderByDatePaiementDescIdDesc(Collection<Long> coursIds);
}

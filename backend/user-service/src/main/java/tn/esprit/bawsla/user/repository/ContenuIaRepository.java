package tn.esprit.bawsla.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.user.entity.ContenuIa;
import tn.esprit.bawsla.user.entity.TypeContenuIa;

public interface ContenuIaRepository extends JpaRepository<ContenuIa, Long> {

    Optional<ContenuIa> findByUserIdAndTypeAndCible(Long userId, TypeContenuIa type, Long cible);
}

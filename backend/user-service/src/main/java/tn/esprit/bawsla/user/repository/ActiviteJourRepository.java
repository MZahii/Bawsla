package tn.esprit.bawsla.user.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.user.entity.ActiviteJour;

public interface ActiviteJourRepository extends JpaRepository<ActiviteJour, Long> {

    boolean existsByUserIdAndJour(Long userId, LocalDate jour);

    long countByUserIdAndJourGreaterThanEqual(Long userId, LocalDate depuis);
}

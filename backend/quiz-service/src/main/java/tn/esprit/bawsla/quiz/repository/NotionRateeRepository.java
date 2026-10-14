package tn.esprit.bawsla.quiz.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.quiz.entity.NotionRatee;

public interface NotionRateeRepository extends JpaRepository<NotionRatee, Long> {

    Optional<NotionRatee> findByReponseId(Long reponseId);
}

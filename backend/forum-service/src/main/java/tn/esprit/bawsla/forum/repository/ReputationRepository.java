package tn.esprit.bawsla.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Reputation;

public interface ReputationRepository extends JpaRepository<Reputation, Long> {
}

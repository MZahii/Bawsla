package tn.esprit.bawsla.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.ParametreForum;

public interface ParametreForumRepository extends JpaRepository<ParametreForum, String> {
}

package tn.esprit.bawsla.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.user.entity.Avatar;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {
}

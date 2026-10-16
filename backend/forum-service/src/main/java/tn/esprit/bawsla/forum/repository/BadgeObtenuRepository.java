package tn.esprit.bawsla.forum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.BadgeObtenu;
import tn.esprit.bawsla.forum.entity.enums.CodeBadge;

public interface BadgeObtenuRepository extends JpaRepository<BadgeObtenu, Long> {

    List<BadgeObtenu> findByUserId(Long userId);

    boolean existsByUserIdAndCode(Long userId, CodeBadge code);
}

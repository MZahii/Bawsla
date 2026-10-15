package tn.esprit.bawsla.forum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.PassageCours;

public interface PassageCoursRepository extends JpaRepository<PassageCours, Long> {

    List<PassageCours> findByCoursIdOrderByOrdre(Long coursId);

    long countByCoursId(Long coursId);

    @Modifying
    @Query("delete from PassageCours p where p.coursId = :coursId")
    int supprimerDuCours(@Param("coursId") Long coursId);
}

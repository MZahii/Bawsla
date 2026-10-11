package tn.esprit.bawsla.cours.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.cours.entity.PageDocument;

public interface PageDocumentRepository extends JpaRepository<PageDocument, Long> {

    List<PageDocument> findByCoursIdOrderByNumeroAsc(Long coursId);

    @Modifying
    @Query("delete from PageDocument p where p.coursId = :coursId")
    void supprimerDuCours(@Param("coursId") Long coursId);
}

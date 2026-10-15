package tn.esprit.bawsla.cours.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.cours.entity.Inscription;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

    Optional<Inscription> findByEtudiantIdAndCoursId(Long etudiantId, Long coursId);

    List<Inscription> findByEtudiantIdOrderByDateInscriptionDescIdDesc(Long etudiantId);

    List<Inscription> findByCoursIdOrderByDateInscriptionAscIdAsc(Long coursId);

    /** Une ligne par cours inscrit : [coursId, nombre d'inscrits]. */
    @Query("select i.coursId, count(i) from Inscription i where i.coursId in :ids group by i.coursId")
    List<Object[]> compterParCours(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from Inscription i where i.coursId = :coursId")
    void supprimerDuCours(@Param("coursId") Long coursId);
}

package tn.esprit.bawsla.cours.repository;

import java.util.List;
import java.util.Optional;

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

    Optional<PageDocument> findByCoursIdAndNumero(Long coursId, int numero);

    /** Pages-images déposées par le formateur. */
    List<PageDocument> findByCoursIdAndImageStockageIsNotNullOrderByNumeroAsc(Long coursId);

    /** Dernier numéro de page du cours (0 s'il n'y en a pas). */
    @Query("select coalesce(max(p.numero), 0) from PageDocument p where p.coursId = :coursId")
    int dernierNumero(@Param("coursId") Long coursId);

    /** Nouvelle version du PDF : seules les pages du PDF sont supprimées, les pages-images sont conservées. */
    @Modifying
    @Query("delete from PageDocument p where p.coursId = :coursId and p.imageStockage is null")
    void supprimerPagesPdf(@Param("coursId") Long coursId);
}

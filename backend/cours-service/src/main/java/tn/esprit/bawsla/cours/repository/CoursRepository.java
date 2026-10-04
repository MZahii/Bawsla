package tn.esprit.bawsla.cours.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.Niveau;

public interface CoursRepository extends JpaRepository<Cours, Long> {

    /** Filtres optionnels : un paramètre null est ignoré. */
    @Query("""
            select c from Cours c
            where (:categorie is null or c.categorie = :categorie)
              and (:niveau is null or c.niveau = :niveau)
            order by c.dateCreation desc
            """)
    List<Cours> search(@Param("categorie") String categorie, @Param("niveau") Niveau niveau);
}

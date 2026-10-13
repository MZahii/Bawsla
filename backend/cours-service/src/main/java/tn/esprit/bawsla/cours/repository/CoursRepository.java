package tn.esprit.bawsla.cours.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.entity.StatutCours;

public interface CoursRepository extends JpaRepository<Cours, Long> {

    /**
     * Filtres optionnels (un paramètre null est ignoré) et visibilité : {@code tout} = true pour l'ADMIN ;
     * sinon seuls les cours {@code publie} et ceux de {@code moi} (identifiant sans correspondance pour un étudiant).
     */
    @Query("""
            select c from Cours c
            where (:categorie is null or c.categorie = :categorie)
              and (:niveau is null or c.niveau = :niveau)
              and (:tout = true or c.statut = :publie or c.enseignantId = :moi)
            order by c.dateCreation desc
            """)
    List<Cours> search(@Param("categorie") String categorie, @Param("niveau") Niveau niveau,
                       @Param("tout") boolean tout, @Param("publie") StatutCours publie, @Param("moi") Long moi);

    /** Cours dont la matière n'est pas exactement un libellé de la liste (migration au démarrage). */
    List<Cours> findByCategorieIsNotNullAndCategorieNotIn(Collection<String> matieres);

    /** Anciennes catégories informatiques -> matière « Informatique » (mise à jour groupée, sans toucher aux dates de modification). */
    @Modifying
    @Query("update Cours c set c.categorie = :matiere where c.categorie in :anciennes")
    int migrerVersInformatique(@Param("matiere") String matiere, @Param("anciennes") Collection<String> anciennes);
}

package tn.esprit.bawsla.forum.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.Etiquette;

public interface EtiquetteRepository extends JpaRepository<Etiquette, Long> {

    Optional<Etiquette> findByLibelle(String libelle);

    /** Autocomplétion sur toute la plateforme, les plus utilisées d'abord. */
    @Query("select e from Etiquette e where e.libelle like concat(:prefixe, '%') "
            + "order by e.nbUtilisations desc, e.libelle")
    List<Etiquette> rechercher(@Param("prefixe") String prefixe, Pageable page);

    /** Étiquettes utilisées dans les discussions visibles d'un cours, les plus fréquentes d'abord. */
    @Query("select e from Discussion d join d.etiquettes e where d.coursId = :coursId and d.supprimeeLe is null "
            + "and e.libelle like concat(:prefixe, '%') group by e order by count(d) desc, e.libelle")
    List<Etiquette> rechercherDansCours(@Param("coursId") Long coursId, @Param("prefixe") String prefixe,
                                        Pageable page);
}

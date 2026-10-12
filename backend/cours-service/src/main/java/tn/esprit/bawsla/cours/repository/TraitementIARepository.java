package tn.esprit.bawsla.cours.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.entity.TraitementIA;

public interface TraitementIARepository extends JpaRepository<TraitementIA, Long> {

    Optional<TraitementIA> findFirstByCoursIdOrderByIdDesc(Long coursId);

    /** Le dernier suivi d'une portée donnée ("FICHE" ou "EXPLICATION:fr:MONOLOGUE"…). */
    Optional<TraitementIA> findFirstByCoursIdAndPorteeOrderByIdDesc(Long coursId, String portee);

    /** Les suivis créés avant l'existence des portées étaient tous des fiches. */
    @Modifying
    @Query("update TraitementIA t set t.portee = 'FICHE' where t.portee is null")
    int rattacherALaFiche();

    List<TraitementIA> findByCoursIdAndExecutionOrderByIdAsc(Long coursId, String execution);

    List<TraitementIA> findByStatutIn(Collection<StatutTraitement> statuts);

    List<TraitementIA> findByCoursId(Long coursId);
}

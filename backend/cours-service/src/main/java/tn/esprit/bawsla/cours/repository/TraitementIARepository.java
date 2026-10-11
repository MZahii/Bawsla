package tn.esprit.bawsla.cours.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.entity.TraitementIA;

public interface TraitementIARepository extends JpaRepository<TraitementIA, Long> {

    Optional<TraitementIA> findFirstByCoursIdOrderByIdDesc(Long coursId);

    List<TraitementIA> findByCoursIdAndExecutionOrderByIdAsc(Long coursId, String execution);

    List<TraitementIA> findByStatutIn(Collection<StatutTraitement> statuts);

    List<TraitementIA> findByCoursId(Long coursId);
}

package tn.esprit.bawsla.cours.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.cours.entity.Chapitre;

public interface ChapitreRepository extends JpaRepository<Chapitre, Long> {

    /** Le chapitre appartient-il à ce cours ? */
    boolean existsByIdAndCoursId(Long id, Long coursId);

    /** Une ligne par cours : [coursId, nombre de chapitres, durée totale en minutes]. */
    @Query("""
            select ch.cours.id, count(ch), coalesce(sum(ch.dureeMinutes), 0)
            from Chapitre ch
            where ch.cours.id in :ids
            group by ch.cours.id
            """)
    List<Object[]> statistiques(@Param("ids") Collection<Long> ids);
}

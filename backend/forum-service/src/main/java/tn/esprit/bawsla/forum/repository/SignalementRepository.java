package tn.esprit.bawsla.forum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.Signalement;
import tn.esprit.bawsla.forum.entity.enums.CategorieModeration;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;

public interface SignalementRepository extends JpaRepository<Signalement, Long> {

    /** Signalements faits par un membre et confirmés par la modération (badge Gardien). */
    long countByAuteurIdAndStatut(Long auteurId, StatutSignalement statut);

    boolean existsByAuteurIdAndCibleTypeAndCibleIdAndStatut(Long auteurId, CibleType cibleType, Long cibleId,
                                                           StatutSignalement statut);

    /** Autres signalements encore ouverts sur le même message (tranchés avec lui). */
    List<Signalement> findByCibleTypeAndCibleIdAndStatut(CibleType cibleType, Long cibleId, StatutSignalement statut);

    /** File d'un cours : escalades et scores de l'IA les plus élevés d'abord. */
    @Query("""
            select s from Signalement s where s.coursId = :coursId and (:statut is null or s.statut = :statut)
            order by s.escalade desc, s.creeLe desc, s.id desc""")
    List<Signalement> fileDuCours(@Param("coursId") Long coursId, @Param("statut") StatutSignalement statut);

    /** File de l'admin (toute la plateforme) ; escaladeSeule = escalades des formateurs et décisions de l'IA. */
    @Query("""
            select s from Signalement s where (:statut is null or s.statut = :statut)
              and (:categorie is null or s.categorieIa = :categorie)
              and (:escaladeSeule = false or s.escalade = true or s.auteurId is null)
            order by s.escalade desc, s.creeLe desc, s.id desc""")
    List<Signalement> fileAdmin(@Param("statut") StatutSignalement statut,
                                @Param("categorie") CategorieModeration categorie,
                                @Param("escaladeSeule") boolean escaladeSeule);

    List<Signalement> findByAuteurCibleIdOrderByCreeLeDesc(Long auteurCibleId);

    long countByStatut(StatutSignalement statut);

    long countByAuteurCibleIdAndStatut(Long auteurCibleId, StatutSignalement statut);
}

package tn.esprit.bawsla.cours.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     * Lot 2 : ajoute UNE minute de façon atomique (une seule requête, sûre même avec plusieurs onglets ouverts), seulement si le dernier
     * signal accepté date d'au moins 55 s ({@code limite}) et si le plafond du jour n'est pas atteint. Renvoie 1 si la minute est comptée.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Inscription i set
              i.tempsPasseMinutes = coalesce(i.tempsPasseMinutes, 0) + 1,
              i.minutesDuJour = case when i.jourActivite = :jour then coalesce(i.minutesDuJour, 0) + 1 else 1 end,
              i.jourActivite = :jour, i.dernierSignal = :maintenant, i.dateDerniereActivite = :maintenant
            where i.id = :id
              and (i.dernierSignal is null or i.dernierSignal <= :limite)
              and (i.jourActivite is null or i.jourActivite <> :jour or coalesce(i.minutesDuJour, 0) < :plafond)""")
    int compterUneMinute(@Param("id") Long id, @Param("maintenant") LocalDateTime maintenant, @Param("limite") LocalDateTime limite,
                         @Param("jour") LocalDate jour, @Param("plafond") int plafond);

    /** Signal refusé (trop rapproché ou plafond atteint) : l'activité est quand même datée. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Inscription i set i.dateDerniereActivite = :maintenant where i.id = :id")
    int dater(@Param("id") Long id, @Param("maintenant") LocalDateTime maintenant);

    /** Contrat 8 : toutes les inscriptions (ADMIN). */
    List<Inscription> findAllByOrderByIdAsc();

    @Modifying
    @Query("delete from Inscription i where i.coursId = :coursId")
    void supprimerDuCours(@Param("coursId") Long coursId);
}

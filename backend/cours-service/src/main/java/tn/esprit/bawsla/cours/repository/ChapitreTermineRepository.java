package tn.esprit.bawsla.cours.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.cours.entity.ChapitreTermine;

public interface ChapitreTermineRepository extends JpaRepository<ChapitreTermine, Long> {

    boolean existsByInscriptionIdAndChapitreId(Long inscriptionId, Long chapitreId);

    /**
     * Une ligne par inscription : [inscriptionId, chapitres terminés]. Seuls les chapitres qui existent encore dans le cours comptent
     * (un chapitre supprimé ne fait jamais monter la progression, même si sa ligne n'a pas encore été nettoyée).
     */
    @Query("""
            select ct.inscription.id, count(ct)
            from ChapitreTermine ct
            where ct.inscription.id in :ids
              and exists (select 1 from Chapitre ch where ch.id = ct.chapitreId and ch.cours.id = ct.inscription.coursId)
            group by ct.inscription.id
            """)
    List<Object[]> compterParInscription(@Param("ids") Collection<Long> ids);

    /** Identifiants des chapitres terminés de l'inscription (seulement ceux qui existent encore dans le cours). */
    @Query("""
            select ct.chapitreId
            from ChapitreTermine ct
            where ct.inscription.id = :inscriptionId
              and exists (select 1 from Chapitre ch where ch.id = ct.chapitreId and ch.cours.id = ct.inscription.coursId)
            order by ct.chapitreId
            """)
    List<Long> chapitresTermines(@Param("inscriptionId") Long inscriptionId);

    @Modifying
    @Query("delete from ChapitreTermine ct where ct.inscription.id = :inscriptionId and ct.chapitreId = :chapitreId")
    int supprimer(@Param("inscriptionId") Long inscriptionId, @Param("chapitreId") Long chapitreId);

    @Modifying
    @Query("delete from ChapitreTermine ct where ct.inscription.id = :inscriptionId")
    void supprimerDeLInscription(@Param("inscriptionId") Long inscriptionId);

    @Modifying
    @Query("delete from ChapitreTermine ct where ct.chapitreId = :chapitreId")
    void supprimerDuChapitre(@Param("chapitreId") Long chapitreId);

    @Modifying
    @Query("delete from ChapitreTermine ct where ct.inscription.id in (select i.id from Inscription i where i.coursId = :coursId)")
    void supprimerDuCours(@Param("coursId") Long coursId);
}

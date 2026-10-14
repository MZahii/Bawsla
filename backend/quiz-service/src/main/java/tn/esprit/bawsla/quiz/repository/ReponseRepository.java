package tn.esprit.bawsla.quiz.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.quiz.dto.NotionStat;
import tn.esprit.bawsla.quiz.entity.Reponse;

public interface ReponseRepository extends JpaRepository<Reponse, Long> {

    /** Réponses d'un apprenant agrégées par compétence : base du contrat notions-ratees. */
    @Query("""
            select new tn.esprit.bawsla.quiz.dto.NotionStat(c.libelle, c.coursId,
                   sum(case when r.correcte = false then 1L else 0L end), count(r))
            from Reponse r join r.question q join q.competence c
            where r.tentative.etudiantId = :etudiantId
            group by c.libelle, c.coursId
            """)
    List<NotionStat> statistiquesParCompetence(@Param("etudiantId") Long etudiantId);
}

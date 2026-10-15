package tn.esprit.bawsla.forum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.PassageForum;

public interface PassageForumRepository extends JpaRepository<PassageForum, Long> {

    boolean existsByReponseId(Long reponseId);

    boolean existsByReponseIaId(Long reponseIaId);

    /**
     * Base de connaissances du forum d'un cours, validée par un humain AU MOMENT de la lecture : une réponse encore
     * visible ET acceptée ou approuvée, ou une réponse de La Vigie validée ou corrigée. Retirer une acceptation,
     * masquer une réponse ou rejeter une réponse de La Vigie la sort donc aussitôt de la base.
     */
    @Query("""
            select p from PassageForum p where p.coursId = :coursId
              and (p.reponseId is null or exists (select r.id from Reponse r where r.id = p.reponseId
                     and r.supprimeeLe is null and r.masquee = false
                     and (r.acceptee = true or r.approuveeParId is not null)))
              and (p.reponseIaId is null or exists (select ri.id from ReponseIa ri where ri.id = p.reponseIaId
                     and ri.statut in (tn.esprit.bawsla.forum.entity.enums.StatutReponseIa.VALIDEE,
                                       tn.esprit.bawsla.forum.entity.enums.StatutReponseIa.CORRIGEE)))
              and exists (select d.id from Discussion d where d.id = p.discussionId and d.supprimeeLe is null
                     and d.masquee = false)""")
    List<PassageForum> valides(@Param("coursId") Long coursId);

    List<PassageForum> findByEmbeddingIsNullAndDiscussionId(Long discussionId);
}

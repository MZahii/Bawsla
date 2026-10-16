package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.Sanction;
import tn.esprit.bawsla.forum.entity.enums.TypeSanction;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {

    @Query("""
            select s from Sanction s where s.userId = :userId and s.leveeLe is null
              and s.type in :types and s.dateDebut <= :maintenant
              and (s.dateFin is null or s.dateFin > :maintenant)
            order by s.dateFin desc nulls first""")
    List<Sanction> actives(@Param("userId") Long userId, @Param("types") Collection<TypeSanction> types,
                           @Param("maintenant") LocalDateTime maintenant);

    List<Sanction> findByUserIdOrderByDateDebutDesc(Long userId);
}

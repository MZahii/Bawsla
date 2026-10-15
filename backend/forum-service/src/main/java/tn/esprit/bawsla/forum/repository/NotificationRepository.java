package tn.esprit.bawsla.forum.repository;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDestinataireIdOrderByCreeLeDescIdDesc(Long destinataireId, Pageable page);

    Page<Notification> findByDestinataireIdAndLueFalseOrderByCreeLeDescIdDesc(Long destinataireId, Pageable page);

    long countByDestinataireIdAndLueFalse(Long destinataireId);

    @Modifying
    @Query("update Notification n set n.lue = true where n.destinataireId = :destinataireId and n.lue = false")
    int toutMarquerLu(@Param("destinataireId") Long destinataireId);

    @Modifying
    @Query("update Notification n set n.lue = true where n.destinataireId = :destinataireId and n.id in :ids")
    int marquerLues(@Param("destinataireId") Long destinataireId, @Param("ids") Collection<Long> ids);
}

package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Dernière lecture d'un fil par un utilisateur : un fil est « non lu » si derniereActiviteLe > dernierLuLe.
 */
@Entity
@Table(name = "lecture",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lecture_cible", columnNames = {"userId", "discussionId"})})
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long discussionId;

    @Column(nullable = false)
    private LocalDateTime dernierLuLe;

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public LocalDateTime getDernierLuLe() { return dernierLuLe; }
    public void setDernierLuLe(LocalDateTime dernierLuLe) { this.dernierLuLe = dernierLuLe; }
}

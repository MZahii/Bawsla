package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Signet d'un utilisateur sur un fil.
 */
@Entity
@Table(name = "favori",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_favori_cible", columnNames = {"userId", "discussionId"})},
        indexes = {
                @Index(name = "ix_favori_discussion", columnList = "discussionId")})
public class Favori {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long discussionId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

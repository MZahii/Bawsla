package tn.esprit.bawsla.forum.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Vue comptée d'un fil : une par utilisateur, par fil et par jour.
 */
@Entity
@Table(name = "vue",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vue_jour", columnNames = {"userId", "discussionId", "jour"})})
public class Vue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long discussionId;

    @Column(nullable = false)
    private LocalDate jour;

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public LocalDate getJour() { return jour; }
    public void setJour(LocalDate jour) { this.jour = jour; }
}

package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.CodeBadge;

/**
 * Badge obtenu par un utilisateur (une seule fois par badge).
 */
@Entity
@Table(name = "badge_obtenu",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_badge_obtenu_code", columnNames = {"userId", "code"})})
public class BadgeObtenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private CodeBadge code;

    @Column(nullable = false)
    private LocalDateTime obtenuLe;

    @PrePersist
    void onCreate() {
        if (obtenuLe == null) {
            obtenuLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public CodeBadge getCode() { return code; }
    public void setCode(CodeBadge code) { this.code = code; }

    public LocalDateTime getObtenuLe() { return obtenuLe; }
    public void setObtenuLe(LocalDateTime obtenuLe) { this.obtenuLe = obtenuLe; }
}

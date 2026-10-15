package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tn.esprit.bawsla.forum.entity.enums.RoleMessage;

/**
 * Message d'une conversation privée ; les 6 derniers échanges servent de contexte.
 */
@Entity
@Table(name = "message_vigie",
        indexes = {
                @Index(name = "ix_message_vigie_conversation", columnList = "conversationId")})
public class MessageVigie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private RoleMessage role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texte;

    private Long reponseIaId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public RoleMessage getRole() { return role; }
    public void setRole(RoleMessage role) { this.role = role; }

    public String getTexte() { return texte; }
    public void setTexte(String texte) { this.texte = texte; }

    public Long getReponseIaId() { return reponseIaId; }
    public void setReponseIaId(Long reponseIaId) { this.reponseIaId = reponseIaId; }

    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}

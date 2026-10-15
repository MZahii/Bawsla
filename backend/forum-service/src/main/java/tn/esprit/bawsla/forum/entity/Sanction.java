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
import tn.esprit.bawsla.forum.entity.enums.TypeSanction;

/**
 * Sanction décidée par un ADMIN : avertissement, lecture seule ou suspension (dateFin null = sans fin).
 * Vérifiée côté backend à chaque écriture dans le forum.
 */
@Entity
@Table(name = "sanction",
        indexes = {
                @Index(name = "ix_sanction_user", columnList = "userId")})
public class Sanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TypeSanction type;

    @Column(nullable = false, length = 500)
    private String motif;

    @Column(nullable = false)
    private LocalDateTime dateDebut;

    private LocalDateTime dateFin;

    @Column(nullable = false)
    private Long decidePar;

    private Long signalementId;

    private LocalDateTime leveeLe;

    private Long leveeParId;

    @PrePersist
    void onCreate() {
        if (dateDebut == null) {
            dateDebut = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public TypeSanction getType() { return type; }
    public void setType(TypeSanction type) { this.type = type; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }

    public Long getDecidePar() { return decidePar; }
    public void setDecidePar(Long decidePar) { this.decidePar = decidePar; }

    public Long getSignalementId() { return signalementId; }
    public void setSignalementId(Long signalementId) { this.signalementId = signalementId; }

    public LocalDateTime getLeveeLe() { return leveeLe; }
    public void setLeveeLe(LocalDateTime leveeLe) { this.leveeLe = leveeLe; }

    public Long getLeveeParId() { return leveeParId; }
    public void setLeveeParId(Long leveeParId) { this.leveeParId = leveeParId; }
}

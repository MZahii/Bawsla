package tn.esprit.bawsla.quiz.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entité principale du module Quiz (minimale, à enrichir : Question, Choix, Tentative…).
 * coursId et enseignantId sont des IDENTIFIANTS d'autres services : jamais de clé étrangère.
 */
@Entity
@Table(name = "quiz")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Cours de cours-service, vérifié via Feign à la création. */
    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false)
    private Long enseignantId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @PrePersist
    void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public Long getEnseignantId() { return enseignantId; }
    public void setEnseignantId(Long enseignantId) { this.enseignantId = enseignantId; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public LocalDateTime getDateModification() { return dateModification; }
}

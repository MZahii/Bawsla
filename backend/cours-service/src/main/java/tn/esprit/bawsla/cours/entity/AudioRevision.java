package tn.esprit.bawsla.cours.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Audio MP3 du résumé d'une fiche. Le fichier porte un nom UUID dans le dossier de stockage (voir StockageService). */
@Entity
@Table(name = "audio_revision", indexes = {
        @Index(name = "idx_audio_cours", columnList = "coursId"),
        @Index(name = "idx_audio_fiche", columnList = "ficheId")})
public class AudioRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false)
    private Long ficheId;

    @Column(nullable = false, length = 60)
    private String fichier;

    private Long taille;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateGeneration;

    @PrePersist
    void onCreate() {
        dateGeneration = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getCoursId() { return coursId; }
    public void setCoursId(Long coursId) { this.coursId = coursId; }

    public Long getFicheId() { return ficheId; }
    public void setFicheId(Long ficheId) { this.ficheId = ficheId; }

    public String getFichier() { return fichier; }
    public void setFichier(String fichier) { this.fichier = fichier; }

    public Long getTaille() { return taille; }
    public void setTaille(Long taille) { this.taille = taille; }

    public LocalDateTime getDateGeneration() { return dateGeneration; }
}

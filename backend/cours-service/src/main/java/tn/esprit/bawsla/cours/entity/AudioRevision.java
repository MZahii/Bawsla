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

    /** Type MIME du fichier : audio/mpeg (MP3) ou audio/wav. */
    @Column(length = 40)
    private String mime = "audio/mpeg";

    /** Fournisseur vocal qui a produit l'audio : elevenlabs, gemini, gtts ou edge. */
    @Column(length = 30)
    private String fournisseur;

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

    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }

    public String getFournisseur() { return fournisseur; }
    public void setFournisseur(String fournisseur) { this.fournisseur = fournisseur; }

    public LocalDateTime getDateGeneration() { return dateGeneration; }
}

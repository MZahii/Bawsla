package tn.esprit.bawsla.cours.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entité principale du module Cours.
 * enseignantId référence un utilisateur de user-service PAR IDENTIFIANT (pas de clé étrangère).
 */
@Entity
@Table(name = "cours")
public class Cours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String categorie;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Niveau niveau;

    /** Prix en dinars ; 0 = gratuit. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prix = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutCours statut = StatutCours.BROUILLON;

    /** Nom du fichier image sur disque (UUID + extension), jamais le nom d'origine. */
    @Column(length = 60)
    private String imageFichier;

    /** Nom d'origine du PDF, pour l'affichage seulement (le fichier sur disque porte un nom UUID). */
    private String fichierPdf;

    @Column(length = 60)
    private String pdfStockage;

    private Long pdfTaille;

    @Column(nullable = false)
    private Long enseignantId;

    @OneToMany(mappedBy = "cours", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<Chapitre> chapitres = new ArrayList<>();

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

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public Niveau getNiveau() { return niveau; }
    public void setNiveau(Niveau niveau) { this.niveau = niveau; }

    public BigDecimal getPrix() { return prix; }
    public void setPrix(BigDecimal prix) { this.prix = prix; }

    public StatutCours getStatut() { return statut; }
    public void setStatut(StatutCours statut) { this.statut = statut; }

    public String getImageFichier() { return imageFichier; }
    public void setImageFichier(String imageFichier) { this.imageFichier = imageFichier; }

    public String getFichierPdf() { return fichierPdf; }
    public void setFichierPdf(String fichierPdf) { this.fichierPdf = fichierPdf; }

    public String getPdfStockage() { return pdfStockage; }
    public void setPdfStockage(String pdfStockage) { this.pdfStockage = pdfStockage; }

    public Long getPdfTaille() { return pdfTaille; }
    public void setPdfTaille(Long pdfTaille) { this.pdfTaille = pdfTaille; }

    public Long getEnseignantId() { return enseignantId; }
    public void setEnseignantId(Long enseignantId) { this.enseignantId = enseignantId; }

    public List<Chapitre> getChapitres() { return chapitres; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public LocalDateTime getDateModification() { return dateModification; }
}

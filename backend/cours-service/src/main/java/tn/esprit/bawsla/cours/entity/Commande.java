package tn.esprit.bawsla.cours.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Achat d'un cours payant (paiement SIMULÉ : aucun prestataire, aucune somme débitée). On ne garde que l'identifiant de l'apprenant,
 * le montant payé au moment de l'achat et les 4 derniers chiffres de la carte : ni numéro complet, ni cryptogramme, ni nom du titulaire.
 * Une seule commande par apprenant et par cours. Elle survit à la désinscription (l'apprenant peut revenir sans repayer).
 */
@Entity
@Table(name = "commande",
        uniqueConstraints = @UniqueConstraint(name = "uk_commande_etudiant_cours", columnNames = {"etudiantId", "coursId"}),
        indexes = @Index(name = "idx_commande_cours", columnList = "coursId"))
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @Column(nullable = false)
    private Long etudiantId;

    @Column(nullable = false)
    private Long coursId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 4)
    private String derniersChiffres;

    @Column(nullable = false, length = 20)
    private String marque;

    @Column(nullable = false)
    private LocalDateTime datePaiement;

    protected Commande() {
    }

    public Commande(String reference, Long etudiantId, Long coursId, BigDecimal montant, String derniersChiffres, String marque) {
        this.reference = reference;
        this.etudiantId = etudiantId;
        this.coursId = coursId;
        this.montant = montant;
        this.derniersChiffres = derniersChiffres;
        this.marque = marque;
        this.datePaiement = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Long getEtudiantId() { return etudiantId; }
    public Long getCoursId() { return coursId; }
    public BigDecimal getMontant() { return montant; }
    public String getDerniersChiffres() { return derniersChiffres; }
    public String getMarque() { return marque; }
    public LocalDateTime getDatePaiement() { return datePaiement; }
}

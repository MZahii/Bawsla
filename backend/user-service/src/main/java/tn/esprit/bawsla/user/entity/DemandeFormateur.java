package tn.esprit.bawsla.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Demande pour devenir formateur (même base que User : une vraie clé étrangère est permise ici). */
@Entity
@Table(name = "demandes_formateur")
public class DemandeFormateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private User utilisateur;

    @Column(nullable = false, length = 200)
    private String expertise;

    @Column(length = 1000)
    private String presentation;

    @Column(length = 300)
    private String lien;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutDemande statut = StatutDemande.EN_ATTENTE;

    /** Obligatoire en cas de refus, visible par le demandeur. */
    @Column(length = 500)
    private String motifRefus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateTraitement;

    /** Id de l'admin qui a traité la demande. */
    private Long traiteePar;

    @PrePersist
    void onCreate() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    public void accepter(Long adminId, LocalDateTime maintenant) {
        statut = StatutDemande.ACCEPTEE;
        motifRefus = null;
        traiteePar = adminId;
        dateTraitement = maintenant;
    }

    public void refuser(Long adminId, String motif, LocalDateTime maintenant) {
        statut = StatutDemande.REFUSEE;
        motifRefus = motif;
        traiteePar = adminId;
        dateTraitement = maintenant;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUtilisateur() { return utilisateur; }
    public void setUtilisateur(User utilisateur) { this.utilisateur = utilisateur; }

    public String getExpertise() { return expertise; }
    public void setExpertise(String expertise) { this.expertise = expertise; }

    public String getPresentation() { return presentation; }
    public void setPresentation(String presentation) { this.presentation = presentation; }

    public String getLien() { return lien; }
    public void setLien(String lien) { this.lien = lien; }

    public StatutDemande getStatut() { return statut; }

    public String getMotifRefus() { return motifRefus; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDateTraitement() { return dateTraitement; }

    public Long getTraiteePar() { return traiteePar; }
}

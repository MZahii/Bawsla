package tn.esprit.bawsla.user.entity;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** "user" est un mot réservé SQL : la table s'appelle "users". */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /** Hash BCrypt, jamais le mot de passe en clair, jamais renvoyé par l'API. */
    @Column(nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Présentation libre, affichée sur le profil. */
    @Column(length = 500)
    private String bio;

    @Column(nullable = false)
    private boolean actif = true;

    /**
     * Formateur validé par l'admin : seul un formateur validé peut publier un cours (contrat GET /api/users/{id},
     * lu par cours-service). Toujours false pour les autres rôles.
     */
    @Column(nullable = false)
    private boolean formateurValide;

    /** Horodatage (ms) de la photo de profil, null sans photo : sert aussi à rafraîchir le cache du front. */
    private Long avatarVersion;

    @Embedded
    private Parcours parcours;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /** Dernière connexion réussie (null tant que l'utilisateur ne s'est jamais connecté). */
    private LocalDateTime derniereConnexion;

    /** Échecs de connexion consécutifs, remis à zéro à la connexion suivante réussie. */
    @Column(nullable = false)
    private int tentativesEchouees;

    /** Connexion refusée jusqu'à cette date après trop d'échecs (protection contre le brute force). */
    private LocalDateTime verrouilleJusqua;

    @PrePersist
    void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public boolean isFormateurValide() { return formateurValide; }
    public void setFormateurValide(boolean formateurValide) { this.formateurValide = formateurValide; }

    public Long getAvatarVersion() { return avatarVersion; }
    public void setAvatarVersion(Long avatarVersion) { this.avatarVersion = avatarVersion; }

    public Parcours getParcours() { return parcours; }
    public void setParcours(Parcours parcours) { this.parcours = parcours; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public LocalDateTime getDerniereConnexion() { return derniereConnexion; }

    public int getTentativesEchouees() { return tentativesEchouees; }

    public LocalDateTime getVerrouilleJusqua() { return verrouilleJusqua; }

    public boolean estVerrouille(LocalDateTime maintenant) {
        return verrouilleJusqua != null && verrouilleJusqua.isAfter(maintenant);
    }

    /** Compte un échec ; au-delà de {@code maxTentatives}, verrouille le compte pendant {@code duree}. */
    public void enregistrerEchec(LocalDateTime maintenant, int maxTentatives, Duration duree) {
        tentativesEchouees++;
        if (tentativesEchouees >= maxTentatives) {
            verrouilleJusqua = maintenant.plus(duree);
            tentativesEchouees = 0;
        }
    }

    public void enregistrerConnexion(LocalDateTime maintenant) {
        derniereConnexion = maintenant;
        deverrouiller();
    }

    public void deverrouiller() {
        tentativesEchouees = 0;
        verrouilleJusqua = null;
    }
}

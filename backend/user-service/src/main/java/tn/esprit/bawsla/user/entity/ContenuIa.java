package tn.esprit.bawsla.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Dernier texte généré par le LLM pour un utilisateur (conseil, plan, explication du risque d'un apprenant, rapport de
 * la classe). Il est réutilisé tant que les données envoyées au LLM n'ont pas changé ({@code empreinte} : SHA-256 de la
 * requête) : un nouveau clic ne consomme pas le quota du fournisseur. {@code cible} : l'apprenant expliqué, 0 sinon.
 */
@Entity
@Table(name = "contenus_ia", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "type", "cible"}))
public class ContenuIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeContenuIa type;

    @Column(nullable = false)
    private Long cible;

    @Column(nullable = false, length = 64)
    private String empreinte;

    /** Réponse de l'IA (JSON), telle que renvoyée au front. */
    @Lob
    @Column(nullable = false)
    private String contenu;

    @Column(length = 20)
    private String fournisseur;

    @Column(nullable = false)
    private LocalDateTime dateGeneration;

    protected ContenuIa() {
    }

    public ContenuIa(Long userId, TypeContenuIa type, Long cible) {
        this.userId = userId;
        this.type = type;
        this.cible = cible;
    }

    public void remplacer(String empreinte, String contenu, String fournisseur, LocalDateTime dateGeneration) {
        this.empreinte = empreinte;
        this.contenu = contenu;
        this.fournisseur = fournisseur;
        this.dateGeneration = dateGeneration;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public TypeContenuIa getType() { return type; }
    public Long getCible() { return cible; }
    public String getEmpreinte() { return empreinte; }
    public String getContenu() { return contenu; }
    public String getFournisseur() { return fournisseur; }
    public LocalDateTime getDateGeneration() { return dateGeneration; }
}

package tn.esprit.bawsla.forum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Étiquette (en minuscules, unique sur la plateforme), partagée par toutes les discussions.
 */
@Entity
@Table(name = "etiquette")
public class Etiquette {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String libelle;

    @Column(nullable = false)
    private int nbUtilisations;

    public Long getId() { return id; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public int getNbUtilisations() { return nbUtilisations; }
    public void setNbUtilisations(int nbUtilisations) { this.nbUtilisations = nbUtilisations; }
}

package tn.esprit.bawsla.forum.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Réglage global (clé / valeur) réglé par l'admin : seuils de modération, limites anti-flood, mots interdits,
 * seuils de similarité. Les valeurs par défaut sont dans le code (ParametresForum).
 */
@Entity
@Table(name = "parametres_forum")
public class ParametreForum {

    @Id
    @Column(length = 80)
    private String cle;

    @Column(nullable = false, length = 2000)
    private String valeur;

    private Long modifieParId;

    private LocalDateTime modifieLe;

    public String getCle() { return cle; }
    public void setCle(String cle) { this.cle = cle; }

    public String getValeur() { return valeur; }
    public void setValeur(String valeur) { this.valeur = valeur; }

    public Long getModifieParId() { return modifieParId; }
    public void setModifieParId(Long modifieParId) { this.modifieParId = modifieParId; }

    public LocalDateTime getModifieLe() { return modifieLe; }
    public void setModifieLe(LocalDateTime modifieLe) { this.modifieLe = modifieLe; }
}

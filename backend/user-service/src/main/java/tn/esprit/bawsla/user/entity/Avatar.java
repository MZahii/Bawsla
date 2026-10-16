package tn.esprit.bawsla.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Photo de profil, dans sa propre table pour ne pas charger l'image à chaque lecture d'un User.
 * L'identifiant est celui de l'utilisateur (une photo au plus par compte).
 */
@Entity
@Table(name = "avatars")
public class Avatar {

    @Id
    private Long userId;

    /** 2 Mo au plus (vérifié par AvatarService) ; MEDIUMBLOB accepte jusqu'à 16 Mo. */
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contenu;

    @Column(nullable = false, length = 30)
    private String typeMime;

    @Column(nullable = false)
    private LocalDateTime dateMaj;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public byte[] getContenu() { return contenu; }
    public void setContenu(byte[] contenu) { this.contenu = contenu; }

    public String getTypeMime() { return typeMime; }
    public void setTypeMime(String typeMime) { this.typeMime = typeMime; }

    public LocalDateTime getDateMaj() { return dateMaj; }
    public void setDateMaj(LocalDateTime dateMaj) { this.dateMaj = dateMaj; }
}

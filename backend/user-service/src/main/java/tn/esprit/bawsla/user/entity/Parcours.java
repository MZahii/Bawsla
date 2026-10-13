package tn.esprit.bawsla.user.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

/**
 * Réponses de l'apprenant au questionnaire d'accueil (toutes facultatives). Elles serviront au module IA
 * (recommandations avant les premiers quiz), sans jamais être envoyées avec le nom ou l'email.
 */
@Embeddable
public class Parcours {

    @Column(name = "parcours_profil", length = 50)
    private String profil;

    @Column(name = "parcours_objectif", length = 150)
    private String objectif;

    @Convert(converter = ListeTexteConverter.class)
    @Column(name = "parcours_domaines", length = 1000)
    private List<String> domaines = new ArrayList<>();

    @Column(name = "parcours_temps", length = 50)
    private String temps;

    public String getProfil() { return profil; }
    public void setProfil(String profil) { this.profil = profil; }

    public String getObjectif() { return objectif; }
    public void setObjectif(String objectif) { this.objectif = objectif; }

    public List<String> getDomaines() { return domaines == null ? new ArrayList<>() : domaines; }
    public void setDomaines(List<String> domaines) { this.domaines = domaines; }

    public String getTemps() { return temps; }
    public void setTemps(String temps) { this.temps = temps; }
}

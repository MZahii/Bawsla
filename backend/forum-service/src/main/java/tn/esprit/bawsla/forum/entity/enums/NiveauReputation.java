package tn.esprit.bawsla.forum.entity.enums;

/** Niveaux de réputation, au thème de la boussole. */
public enum NiveauReputation {
    MOUSSE("Mousse", 0),
    MATELOT("Matelot", 50),
    NAVIGATEUR("Navigateur", 200),
    CARTOGRAPHE("Cartographe", 500),
    CAPITAINE("Capitaine", 1000);

    private final String libelle;
    private final int seuil;

    NiveauReputation(String libelle, int seuil) {
        this.libelle = libelle;
        this.seuil = seuil;
    }

    public String getLibelle() { return libelle; }

    public int getSeuil() { return seuil; }

    /** Niveau atteint avec ce nombre de points (négatif = Mousse). */
    public static NiveauReputation depuisPoints(int points) {
        NiveauReputation niveau = MOUSSE;
        for (NiveauReputation n : values()) {
            if (points >= n.seuil) {
                niveau = n;
            }
        }
        return niveau;
    }

    /** Niveau suivant, ou null au niveau maximal. */
    public NiveauReputation suivant() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }
}

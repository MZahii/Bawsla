package tn.esprit.bawsla.cours.entity;

/** Rôle d'un segment dans le script : seule l'analogie est libre, les autres s'appuient sur le cours. */
public enum TypeSegment {
    INTRO,
    EXPLICATION,
    ANALOGIE,
    EXEMPLE,
    DEMONSTRATION,
    QUESTION,
    RECAP;

    public String code() {
        return name().toLowerCase();
    }

    public static TypeSegment depuis(String valeur) {
        for (TypeSegment t : values()) {
            if (t.code().equalsIgnoreCase(valeur)) {
                return t;
            }
        }
        return EXPLICATION;
    }
}

package tn.esprit.bawsla.cours.entity;

/** Qui parle : le professeur, ou l'étudiant débutant des dialogues. */
public enum Locuteur {
    PROF,
    ETUDIANT;

    public String code() {
        return name().toLowerCase();
    }

    public static Locuteur depuis(String valeur) {
        return ETUDIANT.code().equalsIgnoreCase(valeur) ? ETUDIANT : PROF;
    }
}

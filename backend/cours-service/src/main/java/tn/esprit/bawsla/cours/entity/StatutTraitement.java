package tn.esprit.bawsla.cours.entity;

/** ANNULE : l'étape n'a pas eu lieu parce qu'une étape précédente a échoué. */
public enum StatutTraitement {
    EN_ATTENTE,
    EN_COURS,
    TERMINE,
    ECHEC,
    ANNULE;

    public boolean actif() {
        return this == EN_ATTENTE || this == EN_COURS;
    }
}

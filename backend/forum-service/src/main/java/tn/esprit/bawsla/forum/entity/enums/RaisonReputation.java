package tn.esprit.bawsla.forum.entity.enums;

/** Raisons d'un mouvement de points, avec le barème du plan (§3.6). Annuler un vote rejoue le delta inverse. */
public enum RaisonReputation {
    QUESTION_VOTEE(5),
    REPONSE_VOTEE(10),
    REPONSE_ACCEPTEE(15),
    ACCEPTATION(2),
    APPROBATION_FORMATEUR(20),
    VIGIE_UTILE(1),
    VOTE_NEGATIF_RECU(-2),
    VOTE_NEGATIF_DONNE(-1),
    MESSAGE_MASQUE(-10);

    private final int points;

    RaisonReputation(int points) {
        this.points = points;
    }

    public int getPoints() { return points; }
}

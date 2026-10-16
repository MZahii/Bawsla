package tn.esprit.bawsla.forum.entity.enums;

/** Les 10 badges du forum ; objectif = valeur à atteindre pour la barre de progression. */
public enum CodeBadge {
    PREMIERE_QUESTION("Première question", "Poser ta première question", 1),
    PREMIERE_REPONSE("Première réponse", "Répondre pour la première fois", 1),
    BONNE_QUESTION("Bonne question", "Une question qui reçoit +10 votes", 10),
    REPONSE_ACCEPTEE("Réponse acceptée", "Une de tes réponses est choisie comme meilleure réponse", 1),
    APPROUVE_FORMATEUR("Approuvé par le formateur", "Une de tes réponses est approuvée par le formateur", 1),
    SECOURISTE("Secouriste", "Répondre à une question restée sans réponse plus de 24 h", 1),
    ECLAIREUR("Éclaireur", "Trouver ta réponse dans les questions similaires au lieu de republier", 1),
    PEDAGOGUE("Pédagogue", "10 réponses acceptées", 10),
    ASSIDU("Assidu", "Actif 7 jours d'affilée sur le forum", 7),
    GARDIEN("Gardien", "3 signalements confirmés par la modération", 3);

    private final String libelle;
    private final String description;
    private final int objectif;

    CodeBadge(String libelle, String description, int objectif) {
        this.libelle = libelle;
        this.description = description;
        this.objectif = objectif;
    }

    public String getLibelle() { return libelle; }

    public String getDescription() { return description; }

    public int getObjectif() { return objectif; }
}

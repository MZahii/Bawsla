package tn.esprit.bawsla.cours.entity;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;

/** Monologue (le professeur parle seul) ou dialogue (un étudiant débutant pose des questions). */
public enum ModeExplication {
    MONOLOGUE,
    DIALOGUE;

    /** Code envoyé à l'IA et utilisé dans les URLs : "monologue" ou "dialogue". */
    public String code() {
        return name().toLowerCase();
    }

    public static ModeExplication depuis(String valeur) {
        for (ModeExplication m : values()) {
            if (m.code().equalsIgnoreCase(valeur)) {
                return m;
            }
        }
        throw new BadRequestException("Mode invalide : utilise « monologue » ou « dialogue ».");
    }
}

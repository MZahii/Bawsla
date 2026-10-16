package tn.esprit.bawsla.cours.entity;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;

/** Langue d'une explication orale : le français est généré, l'anglais est la traduction du français validé. */
public enum LangueExplication {
    FR("fr"),
    EN("en");

    private final String code;

    LangueExplication(String code) {
        this.code = code;
    }

    /** Code envoyé à l'IA et utilisé dans les URLs : "fr" ou "en". */
    public String code() {
        return code;
    }

    public static LangueExplication depuis(String valeur) {
        for (LangueExplication l : values()) {
            if (l.code.equalsIgnoreCase(valeur)) {
                return l;
            }
        }
        throw new BadRequestException("Langue invalide : utilise « fr » ou « en ».");
    }
}

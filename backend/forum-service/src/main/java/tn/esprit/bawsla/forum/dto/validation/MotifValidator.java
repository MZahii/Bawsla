package tn.esprit.bawsla.forum.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import tn.esprit.bawsla.forum.service.Repetitions;

/** Règles de {@link Motif}, dans l'ordre où le front les affiche (features/forum/components/validation.ts). */
public class MotifValidator implements ConstraintValidator<Motif, String> {

    public static final String OBLIGATOIRE = "Le motif est obligatoire";
    public static final String REPETITIF = "Le motif est trop répétitif : explique la raison en quelques mots";
    public static final String LONGUEUR = "Le motif fait entre " + Motif.MIN + " et " + Motif.MAX + " caractères";
    public static final String MOTS = "Le motif doit contenir au moins 2 mots";

    /** null si le motif est valable, sinon le message à montrer. */
    public static String erreur(String motif) {
        String t = motif == null ? "" : motif.strip();
        if (t.isEmpty()) {
            return OBLIGATOIRE;
        }
        if (Repetitions.motifRepetitif(t)) {
            return REPETITIF;
        }
        if (t.length() < Motif.MIN || t.length() > Motif.MAX) {
            return LONGUEUR;
        }
        return Repetitions.motsDistincts(t) < 2 ? MOTS : null;
    }

    @Override
    public boolean isValid(String motif, ConstraintValidatorContext context) {
        String erreur = erreur(motif);
        if (erreur == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(erreur).addConstraintViolation();
        return false;
    }
}

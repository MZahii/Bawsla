package tn.esprit.bawsla.cours.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import tn.esprit.bawsla.cours.service.Matieres;

/**
 * Contrainte de {@link CoursRequest} : la matière est obligatoire et fait partie de la liste commune avec le module Quiz ; sa précision
 * (2 à 80 caractères) est obligatoire pour « Autre » et interdite sinon. Les erreurs sont rattachées aux champs {@code categorie} et
 * {@code categoriePrecision}, donc renvoyées dans {@code details} comme les autres erreurs de validation.
 */
@Documented
@Constraint(validatedBy = MatiereValide.Validateur.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MatiereValide {

    String message() default "Matière invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validateur implements ConstraintValidator<MatiereValide, CoursRequest> {

        @Override
        public boolean isValid(CoursRequest requete, ConstraintValidatorContext contexte) {
            if (requete == null) {
                return true;
            }
            var erreurs = Matieres.erreurs(requete.categorie(), requete.categoriePrecision());
            if (erreurs.isEmpty()) {
                return true;
            }
            contexte.disableDefaultConstraintViolation();
            erreurs.forEach((champ, message) -> contexte.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(champ).addConstraintViolation());
            return false;
        }
    }
}

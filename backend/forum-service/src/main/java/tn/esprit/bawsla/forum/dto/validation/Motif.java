package tn.esprit.bawsla.forum.dto.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Motif d'une décision de modération (tracé dans le journal, parfois montré au membre) : obligatoire, de 10 à
 * 500 caractères, au moins 2 mots, pas un texte répétitif. Le message d'erreur dit laquelle de ces règles manque.
 */
@Documented
@Constraint(validatedBy = MotifValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Motif {

    int MIN = 10;
    int MAX = 500;

    String message() default "Motif invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

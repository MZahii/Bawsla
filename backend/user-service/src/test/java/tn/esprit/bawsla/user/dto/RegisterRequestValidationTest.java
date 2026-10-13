package tn.esprit.bawsla.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private Set<String> champsInvalides(String motDePasse) {
        return validator.validate(new RegisterRequest("Ben Ali", "Sami", "sami@example.com", motDePasse, null))
                .stream().map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Password1", "motdepasse9", "12345678é", "Élève2026"})
    void motDePasseValide(String mdp) {
        assertThat(champsInvalides(mdp)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaaaaaaa", "12345678", "Pass1", "        "})
    void motDePasseRefuse(String mdp) {
        assertThat(champsInvalides(mdp)).containsExactly("motDePasse");
    }
}

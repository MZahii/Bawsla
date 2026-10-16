package tn.esprit.bawsla.forum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tn.esprit.bawsla.forum.dto.validation.MotifValidator;

/** Règle des motifs de modération ; le front (validation.spec.ts) vérifie les mêmes cas. */
class MotifValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"Hors sujet", "Publicité pour un site externe", "Insultes envers un apprenant",
            "Doublon de la question n° 12", "Réponse générale, à vérifier", "  Fuite des réponses de l'examen  ",
            "رسالة خارج موضوع الدرس"})
    void unMotifRedigeEstAccepte(String motif) {
        assertThat(MotifValidator.erreur(motif)).isNull();
    }

    @Test
    void unMotifAbsentOuVideEstObligatoire() {
        assertThat(MotifValidator.erreur(null)).isEqualTo(MotifValidator.OBLIGATOIRE);
        assertThat(MotifValidator.erreur("   ")).isEqualTo(MotifValidator.OBLIGATOIRE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kkkkkkk", "kkkkkkkkkkkkkk", "aaaa bbbb", "kjkjkjkjkjkj", "ok ok ok ok", "spam spam spam spam",
            "kkk et zzz", "Noooon pas ça"})
    void unTexteRepetitifEstRefuse(String motif) {
        assertThat(MotifValidator.erreur(motif)).isEqualTo(MotifValidator.REPETITIF);
    }

    @Test
    void laLongueurVaDe10A500Caracteres() {
        assertThat(MotifValidator.erreur("Pas bon")).isEqualTo(MotifValidator.LONGUEUR);
        String long500 = IntStream.range(0, 100).mapToObj(i -> "raison" + i).collect(Collectors.joining(" "));
        assertThat(MotifValidator.erreur(long500.substring(0, 500))).isNull();
        assertThat(MotifValidator.erreur(long500.substring(0, 501))).isEqualTo(MotifValidator.LONGUEUR);
    }

    @Test
    void ilFautAuMoinsDeuxMots() {
        assertThat(MotifValidator.erreur("Inapproprié")).isEqualTo(MotifValidator.MOTS);
        assertThat(MotifValidator.erreur("Doublon !!! ?")).isEqualTo(MotifValidator.MOTS);
    }
}

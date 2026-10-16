package tn.esprit.bawsla.forum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tn.esprit.bawsla.forum.dto.PageResponse;
import tn.esprit.bawsla.forum.entity.VecteurConverter;
import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;
import tn.esprit.bawsla.forum.service.Similarite;
import tn.esprit.bawsla.forum.service.Similarite.Score;

/** Règles du modèle qui ne demandent pas de base : vecteurs, niveaux, pagination. */
class ReglesModeleTest {

    private final VecteurConverter converter = new VecteurConverter();

    @Test
    void unVecteurFaitQuatreOctetsParDimensionEtRevientIdentique() {
        float[] vecteur = new float[768];
        for (int i = 0; i < vecteur.length; i++) {
            vecteur[i] = (float) Math.sin(i);
        }
        byte[] octets = converter.convertToDatabaseColumn(vecteur);

        assertThat(octets).hasSize(768 * 4);
        assertThat(converter.convertToEntityAttribute(octets)).containsExactly(vecteur);
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void leNiveauSuitLesSeuilsDeLaBoussole() {
        assertThat(NiveauReputation.depuisPoints(-12)).isEqualTo(NiveauReputation.MOUSSE);
        assertThat(NiveauReputation.depuisPoints(49)).isEqualTo(NiveauReputation.MOUSSE);
        assertThat(NiveauReputation.depuisPoints(50)).isEqualTo(NiveauReputation.MATELOT);
        assertThat(NiveauReputation.depuisPoints(499)).isEqualTo(NiveauReputation.NAVIGATEUR);
        assertThat(NiveauReputation.depuisPoints(1000)).isEqualTo(NiveauReputation.CAPITAINE);
        assertThat(NiveauReputation.MATELOT.suivant()).isEqualTo(NiveauReputation.NAVIGATEUR);
        assertThat(NiveauReputation.CAPITAINE.suivant()).isNull();
    }

    @Test
    void laFusionRrfFavoriseCeQueLesDeuxClassementsPlacentHaut() {
        List<Score> lexical = List.of(new Score(1L, 9), new Score(2L, 5), new Score(3L, 1));
        List<Score> semantique = List.of(new Score(2L, 0.9), new Score(4L, 0.8), new Score(1L, 0.7));
        List<Score> fusion = Similarite.rrf(List.of(lexical, semantique));
        assertThat(fusion).extracting(Score::id).containsExactly(2L, 1L, 4L, 3L);
        assertThat(fusion.get(0).score()).isEqualTo(1.0 / 62 + 1.0 / 61);
    }

    @Test
    void leCosinusRefuseDeComparerDeuxModelesDeDimensionsDifferentes() {
        assertThat(Similarite.cosinus(new float[] {0.6f, 0.8f}, new float[] {0.6f, 0.8f})).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-6));
        assertThat(Similarite.cosinus(new float[] {1f}, new float[] {1f, 0f})).isEqualTo(-1);
        assertThat(Similarite.cosinus(null, new float[] {1f})).isEqualTo(-1);
    }

    @Test
    void lesMotsIgnorentAccentsCasseEtMotsVides() {
        assertThat(Similarite.mots("Pourquoi les CLÉS étrangères ?")).containsExactlyInAnyOrder("cles", "etrangeres");
        double titre = Similarite.lexical(Similarite.mots("clés étrangères"), "Les clés étrangères", "");
        double contenu = Similarite.lexical(Similarite.mots("clés étrangères"), "Autre", "des clés étrangères");
        assertThat(titre).isEqualTo(1.0);
        assertThat(contenu).isEqualTo(0.5);
    }

    @Test
    void laPageIndiqueSIlResteDesElements() {
        assertThat(PageResponse.of(List.of(1, 2), 0, 20, 45).suivante()).isTrue();
        assertThat(PageResponse.of(List.of(1), 2, 20, 45).suivante()).isFalse();
    }
}

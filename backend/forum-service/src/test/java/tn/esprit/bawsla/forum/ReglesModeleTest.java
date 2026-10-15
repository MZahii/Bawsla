package tn.esprit.bawsla.forum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tn.esprit.bawsla.forum.dto.PageResponse;
import tn.esprit.bawsla.forum.entity.VecteurConverter;
import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;

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
    void laPageIndiqueSIlResteDesElements() {
        assertThat(PageResponse.of(List.of(1, 2), 0, 20, 45).suivante()).isTrue();
        assertThat(PageResponse.of(List.of(1), 2, 20, 45).suivante()).isFalse();
    }
}

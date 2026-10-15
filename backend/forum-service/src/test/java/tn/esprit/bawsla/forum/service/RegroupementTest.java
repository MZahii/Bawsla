package tn.esprit.bawsla.forum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** K-Means sphérique et silhouette (lot 7), sur des vecteurs construits à la main. */
class RegroupementTest {

    /** Trois groupes nets autour de trois axes, avec un peu de bruit. */
    private static List<float[]> troisGroupes() {
        Random r = new Random(7);
        float[][] axes = {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}};
        float[][] v = new float[12][];
        for (int i = 0; i < 12; i++) {
            float[] a = axes[i % 3].clone();
            for (int d = 0; d < 4; d++) {
                a[d] += (float) (r.nextDouble() * 0.2);
            }
            v[i] = a;
        }
        return Arrays.asList(v);
    }

    @Test
    void laSilhouetteChoisitLeBonNombreDeGroupes() {
        Regroupement.Resultat r = Regroupement.meilleur(troisGroupes());
        assertThat(r.k()).isEqualTo(3);
        assertThat(r.silhouette()).isGreaterThan(0.7);
        for (int i = 3; i < 12; i++) {
            assertThat(r.affectation()[i]).isEqualTo(r.affectation()[i % 3]);
        }
    }

    @Test
    void leResultatEstReproductible() {
        assertThat(Regroupement.meilleur(troisGroupes()).affectation())
                .containsExactly(Regroupement.meilleur(troisGroupes()).affectation());
    }

    @Test
    void plusieursDepartsEvitentUnMauvaisOptimumLocal() {
        float[][] x = troisGroupes().stream().map(Regroupement::normaliser).toArray(float[][]::new);
        double[][] d = Regroupement.distances(x);
        double unDepart = Double.POSITIVE_INFINITY;
        for (long graine = 0; graine < 20; graine++) {
            int[] a = Regroupement.kmeans(x, 3, new Random(graine));
            unDepart = Math.min(unDepart, Regroupement.silhouette(d, a, 3));
        }
        double dixDeparts = Regroupement.silhouette(d, Regroupement.kmeans(x, 3, 10, new Random(42)), 3);
        assertThat(dixDeparts).isGreaterThanOrEqualTo(unDepart);
        assertThat(dixDeparts).isGreaterThan(0.7);
    }

    @Test
    void laSilhouetteVautCeQueDitLaDefinition() {
        // Deux paires éloignées : a = 0 dans chaque paire, b = 1 → silhouette 1.
        float[][] x = {{1, 0}, {1, 0}, {0, 1}, {0, 1}};
        assertThat(Regroupement.silhouette(Regroupement.distances(x), new int[]{0, 0, 1, 1}, 2))
                .isCloseTo(1.0, within(1e-9));
        // Un point seul dans son groupe compte 0.
        assertThat(Regroupement.silhouette(Regroupement.distances(x), new int[]{0, 1, 1, 1}, 2)).isLessThan(1.0);
    }

    @Test
    void moinsDeQuatreQuestionsNeDonnentAucunTheme() {
        assertThat(Regroupement.meilleur(troisGroupes().subList(0, 3))).isNull();
    }
}

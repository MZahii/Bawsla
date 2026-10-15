package tn.esprit.bawsla.forum.service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Regroupement des questions par thème (plan §4.7) : K-Means sphérique sur les embeddings (distance = 1 − cosinus),
 * initialisation k-means++ à graine fixe (résultat reproductible), 10 départs dont on garde la plus faible inertie
 * (comme {@code n_init=10} de scikit-learn : un seul départ peut rester coincé dans un mauvais optimum local),
 * k choisi entre 2 et 8 par le meilleur score de silhouette. Calculé en Java, là où sont les vecteurs : quelques centaines de questions se traitent en
 * millisecondes, sans bibliothèque supplémentaire.
 */
final class Regroupement {

    static final int K_MIN = 2;
    static final int K_MAX = 8;
    static final int ITERATIONS = 50;
    static final long GRAINE = 42L;
    static final int DEPARTS = 10;

    /** affectation[i] = groupe de la question i (0 à k−1). */
    record Resultat(int k, int[] affectation, double silhouette) {
    }

    private Regroupement() {
    }

    /** Meilleur regroupement pour k de 2 à min(8, n − 1) ; null s'il y a moins de 4 vecteurs. */
    static Resultat meilleur(List<float[]> vecteurs) {
        int n = vecteurs.size();
        if (n < 4) {
            return null;
        }
        float[][] x = vecteurs.stream().map(Regroupement::normaliser).toArray(float[][]::new);
        double[][] distances = distances(x);
        Resultat meilleur = null;
        for (int k = K_MIN; k <= Math.min(K_MAX, n - 1); k++) {
            int[] affectation = kmeans(x, k, DEPARTS, new Random(GRAINE));
            double s = silhouette(distances, affectation, k);
            if (meilleur == null || s > meilleur.silhouette() + 1e-9) {
                meilleur = new Resultat(k, affectation, Math.round(s * 1000) / 1000.0);
            }
        }
        return meilleur;
    }

    /** Meilleur de plusieurs départs : celui dont la somme des cosinus au centre de son groupe est la plus haute. */
    static int[] kmeans(float[][] x, int k, int departs, Random hasard) {
        int[] meilleure = null;
        double meilleureCohesion = Double.NEGATIVE_INFINITY;
        for (int d = 0; d < departs; d++) {
            int[] affectation = kmeans(x, k, hasard);
            double c = cohesion(x, affectation, k);
            if (c > meilleureCohesion + 1e-9) {
                meilleureCohesion = c;
                meilleure = affectation;
            }
        }
        return meilleure;
    }

    /** Somme des cosinus de chaque point au centre (normalisé) de son groupe : l'inverse de l'inertie. */
    static double cohesion(float[][] x, int[] affectation, int k) {
        float[][] centres = recentrer(x, affectation, k, new float[k][x[0].length]);
        double somme = 0;
        for (int i = 0; i < x.length; i++) {
            somme += Similarite.cosinus(x[i], centres[affectation[i]]);
        }
        return somme;
    }

    static int[] kmeans(float[][] x, int k, Random hasard) {
        int n = x.length;
        float[][] centres = initialiser(x, k, hasard);
        int[] affectation = new int[n];
        Arrays.fill(affectation, -1);
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            boolean change = false;
            for (int i = 0; i < n; i++) {
                int proche = plusProche(x[i], centres);
                if (proche != affectation[i]) {
                    affectation[i] = proche;
                    change = true;
                }
            }
            if (!change) {
                break;
            }
            centres = recentrer(x, affectation, k, centres);
        }
        return affectation;
    }

    /** k-means++ : chaque nouveau centre est tiré proportionnellement au carré de sa distance au centre le plus proche. */
    private static float[][] initialiser(float[][] x, int k, Random hasard) {
        int n = x.length;
        float[][] centres = new float[k][];
        centres[0] = x[hasard.nextInt(n)].clone();
        double[] d2 = new double[n];
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int i = 0; i < n; i++) {
                double d = 1;
                for (int j = 0; j < c; j++) {
                    d = Math.min(d, 1 - Similarite.cosinus(x[i], centres[j]));
                }
                d2[i] = Math.max(0, d) * Math.max(0, d);
                total += d2[i];
            }
            int choisi = 0;
            if (total > 0) {
                double cible = hasard.nextDouble() * total;
                while (choisi < n - 1 && (cible -= d2[choisi]) > 0) {
                    choisi++;
                }
            } else {
                choisi = hasard.nextInt(n);
            }
            centres[c] = x[choisi].clone();
        }
        return centres;
    }

    private static float[][] recentrer(float[][] x, int[] affectation, int k, float[][] anciens) {
        int dimension = x[0].length;
        float[][] centres = new float[k][dimension];
        int[] tailles = new int[k];
        for (int i = 0; i < x.length; i++) {
            tailles[affectation[i]]++;
            for (int d = 0; d < dimension; d++) {
                centres[affectation[i]][d] += x[i][d];
            }
        }
        for (int c = 0; c < k; c++) {
            centres[c] = tailles[c] == 0 ? anciens[c] : normaliser(centres[c]);
        }
        return centres;
    }

    private static int plusProche(float[] v, float[][] centres) {
        int meilleur = 0;
        double max = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < centres.length; c++) {
            double s = Similarite.cosinus(v, centres[c]);
            if (s > max) {
                max = s;
                meilleur = c;
            }
        }
        return meilleur;
    }

    /** Silhouette moyenne (−1 à 1) ; un point seul dans son groupe compte 0, comme dans scikit-learn. */
    static double silhouette(double[][] distances, int[] affectation, int k) {
        int n = affectation.length;
        int[] tailles = new int[k];
        for (int a : affectation) {
            tailles[a]++;
        }
        double somme = 0;
        for (int i = 0; i < n; i++) {
            if (tailles[affectation[i]] <= 1) {
                continue;
            }
            double[] parGroupe = new double[k];
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    parGroupe[affectation[j]] += distances[i][j];
                }
            }
            double a = parGroupe[affectation[i]] / (tailles[affectation[i]] - 1);
            double b = Double.POSITIVE_INFINITY;
            for (int c = 0; c < k; c++) {
                if (c != affectation[i] && tailles[c] > 0) {
                    b = Math.min(b, parGroupe[c] / tailles[c]);
                }
            }
            if (b != Double.POSITIVE_INFINITY && Math.max(a, b) > 0) {
                somme += (b - a) / Math.max(a, b);
            }
        }
        return somme / n;
    }

    static double[][] distances(float[][] x) {
        int n = x.length;
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                d[i][j] = Math.max(0, 1 - Similarite.cosinus(x[i], x[j]));
                d[j][i] = d[i][j];
            }
        }
        return d;
    }

    static float[] normaliser(float[] v) {
        double norme = 0;
        for (float f : v) {
            norme += f * f;
        }
        float[] r = v.clone();
        if (norme > 0) {
            for (int i = 0; i < r.length; i++) {
                r[i] /= (float) Math.sqrt(norme);
            }
        }
        return r;
    }
}

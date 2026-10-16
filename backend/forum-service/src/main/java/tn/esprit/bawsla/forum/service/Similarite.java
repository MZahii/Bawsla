package tn.esprit.bawsla.forum.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Calculs de similarité sans dépendance : cosinus (vecteurs normalisés = produit scalaire), score lexical de repli
 * (H2, ou base sans index FULLTEXT) et fusion des classements par Reciprocal Rank Fusion (RRF, k = 60).
 */
public final class Similarite {

    public static final int K_RRF = 60;

    /** Élément classé : identifiant et score (cosinus, lexical ou RRF). */
    public record Score(Long id, double score) {
    }

    private static final Pattern MOTS = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Set<String> VIDES = Set.of("les", "des", "une", "est", "pas", "que", "qui", "pour", "dans",
            "avec", "sur", "mon", "mes", "son", "ses", "par", "plus", "comme", "sans", "aux", "ces", "cette", "the",
            "and", "for", "how", "why", "what", "does", "pourquoi", "comment", "quoi", "fait", "faire");

    private Similarite() {
    }

    /** Cosinus de deux vecteurs NORMALISÉS ; -1 si les dimensions diffèrent (modèles différents). */
    public static double cosinus(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return -1;
        }
        double somme = 0;
        for (int i = 0; i < a.length; i++) {
            somme += (double) a[i] * b[i];
        }
        return somme;
    }

    /** Mots significatifs d'un texte, en minuscules et sans accents (3 lettres au moins, hors mots vides). */
    public static Set<String> mots(String texte) {
        Set<String> mots = new HashSet<>();
        if (texte == null) {
            return mots;
        }
        String simple = ACCENTS.matcher(Normalizer.normalize(texte.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        var m = MOTS.matcher(simple);
        while (m.find()) {
            String mot = m.group();
            if (mot.length() >= 3 && !VIDES.contains(mot)) {
                mots.add(mot);
            }
        }
        return mots;
    }

    /** Part des mots de la requête présents dans le titre (poids 2) et le contenu (poids 1), entre 0 et 1. */
    public static double lexical(Set<String> requete, String titre, String contenu) {
        if (requete.isEmpty()) {
            return 0;
        }
        Set<String> motsTitre = mots(titre);
        Set<String> motsContenu = mots(contenu);
        double total = 0;
        for (String mot : requete) {
            total += motsTitre.contains(mot) ? 2 : motsContenu.contains(mot) ? 1 : 0;
        }
        return total / (2.0 * requete.size());
    }

    /** Fusion RRF : chaque liste (déjà triée) donne 1 / (k + rang) à ses éléments ; tri décroissant du total. */
    public static List<Score> rrf(Collection<List<Score>> classements) {
        Map<Long, Double> totaux = new LinkedHashMap<>();
        for (List<Score> classement : classements) {
            for (int rang = 0; rang < classement.size(); rang++) {
                totaux.merge(classement.get(rang).id(), 1.0 / (K_RRF + rang + 1), Double::sum);
            }
        }
        List<Score> fusion = new ArrayList<>();
        totaux.forEach((id, s) -> fusion.add(new Score(id, s)));
        fusion.sort(Comparator.comparingDouble(Score::score).reversed());
        return fusion;
    }
}

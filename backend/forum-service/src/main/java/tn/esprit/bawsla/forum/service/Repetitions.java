package tn.esprit.bawsla.forum.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Détection des textes répétitifs, reprise de la règle « repetitions » de la modération
 * ({@code ai-service/app/modules/forum/moderation.py} : même caractère répété, moins de la moitié de mots distincts).
 * Les seuils de la modération visent un message entier ; ceux des motifs, un texte court de 10 à 500 caractères
 * (« kkkkkkk », « ok ok ok ok »). Le front applique la même règle (features/forum/components/validation.ts).
 */
public final class Repetitions {

    private static final Pattern MOT = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");

    /** Motifs : 4 fois la même lettre (aucun mot français n'en a autant), ou au moins 4 mots dont moins de la moitié distincts. */
    public static final int MOTIF_LETTRES = 4;
    public static final int MOTIF_MOTS = 4;
    public static final double RATIO_DISTINCTS = 0.5;

    private static final Pattern LETTRE_REPETEE = Pattern.compile("(\\p{L})\\1{" + (MOTIF_LETTRES - 1) + ",}",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private Repetitions() {
    }

    /** Au moins {@code minMots} mots et moins de la moitié distincts, comparés sans accents ni majuscules (12 mots en modération). */
    public static boolean repetitif(String texte, int minMots) {
        List<String> mots = mots(texte);
        return mots.size() >= minMots && (double) new HashSet<>(mots).size() / mots.size() < RATIO_DISTINCTS;
    }

    /** Règle des motifs : lettre répétée, mot fait d'une seule lettre répétée (« kkk »), mots répétés, ou alphabet pauvre (« kjkjkjkjkj »). */
    public static boolean motifRepetitif(String texte) {
        if (LETTRE_REPETEE.matcher(texte).find() || repetitif(texte, MOTIF_MOTS)) {
            return true;
        }
        Set<Integer> lettres = new HashSet<>();
        for (String mot : mots(texte)) {
            if (mot.length() >= 3 && mot.chars().distinct().count() == 1 && Character.isLetter(mot.charAt(0))) {
                return true;
            }
            mot.chars().filter(Character::isLetter).forEach(lettres::add);
        }
        return texte.strip().length() >= 10 && lettres.size() < 4;
    }

    /** Nombre de mots distincts d'au moins 2 caractères. */
    public static int motsDistincts(String texte) {
        return (int) mots(texte).stream().filter(m -> m.length() >= 2).distinct().count();
    }

    static List<String> mots(String texte) {
        String simple = ACCENTS.matcher(Normalizer.normalize(texte.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        Matcher m = MOT.matcher(simple);
        List<String> mots = new ArrayList<>();
        while (m.find()) {
            mots.add(m.group());
        }
        return mots;
    }
}

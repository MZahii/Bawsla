package tn.esprit.bawsla.cours.service;

import java.util.regex.Pattern;

/**
 * Masque emails et numéros de téléphone du texte extrait des PDF, avant de le renvoyer.
 * Mêmes motifs que le Sanitizer de ai-service (aucune donnée personnelle ne doit sortir vers l'IA).
 */
public final class TexteMasker {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern TELEPHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\s.-]?){7,14}\\d(?!\\d)");

    private TexteMasker() {
    }

    public static String masquer(String texte) {
        if (texte == null || texte.isEmpty()) {
            return "";
        }
        String sansEmail = EMAIL.matcher(texte).replaceAll("[EMAIL]");
        return TELEPHONE.matcher(sansEmail).replaceAll("[TELEPHONE]");
    }
}

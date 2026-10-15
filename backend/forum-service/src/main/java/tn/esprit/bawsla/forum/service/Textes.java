package tn.esprit.bawsla.forum.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;

/** Petits traitements de texte du forum : extrait lisible d'un Markdown, normalisation des étiquettes. */
public final class Textes {

    private static final Pattern BLOC_CODE = Pattern.compile("```.*?(```|$)", Pattern.DOTALL);
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern LIEN = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern MARQUES = Pattern.compile("[*_`>#~|]+");
    private static final Pattern ESPACES = Pattern.compile("\\s+");
    private static final Pattern ETIQUETTE = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#.\\- ]*");

    public static final int MAX_ETIQUETTES = 5;

    private Textes() {
    }

    /** Texte brut d'un Markdown (sans blocs de code ni balises), coupé proprement à {@code longueur} caractères. */
    public static String extrait(String markdown, int longueur) {
        if (markdown == null) {
            return "";
        }
        String texte = BLOC_CODE.matcher(markdown).replaceAll(" ");
        texte = IMAGE.matcher(texte).replaceAll("$1");
        texte = LIEN.matcher(texte).replaceAll("$1");
        texte = MARQUES.matcher(texte).replaceAll("");
        texte = ESPACES.matcher(texte).replaceAll(" ").trim();
        if (texte.length() <= longueur) {
            return texte;
        }
        int coupe = texte.lastIndexOf(' ', longueur);
        return texte.substring(0, coupe > longueur / 2 ? coupe : longueur).trim() + "…";
    }

    /**
     * Étiquettes en minuscules, sans doublon, 40 caractères au plus, 5 au plus.
     * Lettres (y compris arabes), chiffres, espaces et « + # . - » (c++, c#, node.js).
     */
    public static List<String> etiquettes(Collection<String> brutes) {
        if (brutes == null) {
            return List.of();
        }
        Set<String> propres = new LinkedHashSet<>();
        for (String brute : brutes) {
            if (brute == null) {
                continue;
            }
            String e = ESPACES.matcher(brute.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
            if (e.isEmpty()) {
                continue;
            }
            if (e.length() > 40 || !ETIQUETTE.matcher(e).matches()) {
                throw new BadRequestException("Étiquette invalide : « " + brute.trim() + " »");
            }
            propres.add(e);
        }
        if (propres.size() > MAX_ETIQUETTES) {
            throw new BadRequestException("5 étiquettes au plus");
        }
        return new ArrayList<>(propres);
    }
}

package tn.esprit.bawsla.forum.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;

/** Petits traitements de texte du forum : extrait lisible d'un Markdown, normalisation des étiquettes. */
public final class Textes {

    private static final Pattern BLOC_CODE = Pattern.compile("```.*?(```|$)", Pattern.DOTALL);
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern LIEN = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    // Mise en forme Markdown seulement : les caractères du contenu restent (client_id, COUNT(*), SELECT *, 2 * 3).
    private static final Pattern TITRE = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+");
    private static final Pattern CITATION = Pattern.compile("(?m)^[ \\t]{0,3}(?:>[ \\t]?)+");
    private static final Pattern SEPARATEUR_TABLEAU = Pattern.compile("(?m)^[ \\t]*\\|?(?:[ \\t]*:?-{2,}:?[ \\t]*\\|)+[ \\t]*(?::?-{2,}:?)?[ \\t]*$");
    private static final Pattern LIGNE_TABLEAU = Pattern.compile("(?m)^[ \\t]*\\|.*$");
    private static final Pattern CODE_EN_LIGNE = Pattern.compile("`([^`\\n]+)`");
    // Gras, italique et barré : seulement autour d'un texte qui commence et finit par une lettre, un chiffre ou une
    // ponctuation de fin, jamais à l'intérieur d'un mot (client_id, a_b_c) ni seuls (COUNT(*), 2 * 3 * 4).
    private static final String BORD_GAUCHE = "(?<![\\p{L}\\p{N}_*~\\\\])";
    private static final String BORD_DROIT = "(?![\\p{L}\\p{N}_*~])";
    private static final String CONTENU = "(?=[\\p{L}\\p{N}])([^\\n]+?)(?<=[\\p{L}\\p{N}.!?)])";
    private static final List<Pattern> EMPHASES = List.of(
            Pattern.compile(BORD_GAUCHE + "\\*\\*" + CONTENU + "\\*\\*" + BORD_DROIT),
            Pattern.compile(BORD_GAUCHE + "__" + CONTENU + "__" + BORD_DROIT),
            Pattern.compile(BORD_GAUCHE + "~~" + CONTENU + "~~" + BORD_DROIT),
            Pattern.compile(BORD_GAUCHE + "\\*" + CONTENU + "\\*" + BORD_DROIT),
            Pattern.compile(BORD_GAUCHE + "_" + CONTENU + "_" + BORD_DROIT));
    private static final Pattern ESPACES = Pattern.compile("\\s+");
    private static final Pattern ETIQUETTE = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#.\\- ]*");

    public static final int MAX_ETIQUETTES = 5;

    private Textes() {
    }

    /**
     * Texte brut d'un Markdown (sans blocs de code ni mise en forme), coupé proprement à {@code longueur} caractères.
     * Sert à l'affichage, mais aussi à la question envoyée à La Vigie et à la vectorisation : on ne retire que la
     * syntaxe Markdown, jamais un caractère du contenu.
     */
    public static String extrait(String markdown, int longueur) {
        if (markdown == null) {
            return "";
        }
        String texte = BLOC_CODE.matcher(markdown).replaceAll(" ");
        texte = IMAGE.matcher(texte).replaceAll("$1");
        texte = LIEN.matcher(texte).replaceAll("$1");
        texte = TITRE.matcher(texte).replaceAll("");
        texte = CITATION.matcher(texte).replaceAll("");
        texte = SEPARATEUR_TABLEAU.matcher(texte).replaceAll("");
        texte = LIGNE_TABLEAU.matcher(texte).replaceAll(l -> Matcher.quoteReplacement(l.group().replace('|', ' ')));
        texte = sansEmphases(texte);
        texte = ESPACES.matcher(texte).replaceAll(" ").trim();
        if (texte.length() <= longueur) {
            return texte;
        }
        int coupe = texte.lastIndexOf(' ', longueur);
        return texte.substring(0, coupe > longueur / 2 ? coupe : longueur).trim() + "…";
    }

    /** Retire gras, italique et barré hors du code en ligne ; le code en ligne perd ses accents graves, pas son contenu. */
    private static String sansEmphases(String texte) {
        StringBuilder sortie = new StringBuilder();
        Matcher code = CODE_EN_LIGNE.matcher(texte);
        int debut = 0;
        while (code.find()) {
            sortie.append(sansEmphasesHorsCode(texte.substring(debut, code.start()))).append(code.group(1));
            debut = code.end();
        }
        return sortie.append(sansEmphasesHorsCode(texte.substring(debut))).toString();
    }

    private static String sansEmphasesHorsCode(String texte) {
        for (Pattern emphase : EMPHASES) {
            texte = emphase.matcher(texte).replaceAll("$1");
        }
        return texte;
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

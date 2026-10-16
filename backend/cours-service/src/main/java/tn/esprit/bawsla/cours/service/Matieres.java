package tn.esprit.bawsla.cours.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Matières d'un cours, communes avec le module Quiz (mêmes libellés EXACTS, accents compris). La colonne reste un texte : le contrat
 * du champ {@code categorie} ne change pas, mais ses valeurs sont validées.
 *
 * <p>Comparaison stricte, après normalisation Unicode NFC (« é » écrit « e » + accent combinant devient « é ») et suppression des
 * espaces autour : aucune tolérance sur la casse ni sur les accents manquants.</p>
 */
public final class Matieres {

    public static final String AUTRE = "Autre";
    public static final String INFORMATIQUE = "Informatique";
    public static final int PRECISION_MIN = 2;
    public static final int PRECISION_MAX = 80;

    public static final List<String> LISTE = List.of(
            INFORMATIQUE, "Mathématiques", "Physique-Chimie", "Sciences de la vie et de la Terre", "Histoire", "Géographie",
            "Langues", "Économie et gestion", "Psychologie", "Philosophie", "Arts", AUTRE);

    /** Anciennes catégories du catalogue : toutes relevaient de l'informatique. */
    public static final Set<String> ANCIENNES_INFORMATIQUE = Set.of("Données", "Back-end", "Front-end", "DevOps", "Outils");

    public static final String MESSAGE_OBLIGATOIRE = "La matière est obligatoire";
    public static final String MESSAGE_INCONNUE = "Matière inconnue : choisis-en une dans la liste";
    public static final String MESSAGE_PRECISION = "Précise la matière (" + PRECISION_MIN + " à " + PRECISION_MAX + " caractères)";
    public static final String MESSAGE_PRECISION_INTERDITE = "La précision n'est acceptée que pour la matière « " + AUTRE + " »";

    private Matieres() {
    }

    /** Texte normalisé en NFC et sans espaces autour ; null si vide. */
    public static String nettoyer(String texte) {
        if (texte == null) {
            return null;
        }
        String propre = Normalizer.normalize(texte, Normalizer.Form.NFC).strip();
        return propre.isEmpty() ? null : propre;
    }

    /** La matière de la liste qui correspond EXACTEMENT au texte (une fois normalisé), sinon vide. */
    public static Optional<String> canonique(String texte) {
        String propre = nettoyer(texte);
        return propre == null ? Optional.empty() : LISTE.stream().filter(propre::equals).findFirst();
    }

    /**
     * Erreurs de validation par champ ({@code categorie}, {@code categoriePrecision}) ; vide si la matière et sa précision sont valides.
     * Règles : matière obligatoire et dans la liste ; précision de 2 à 80 caractères, obligatoire pour « Autre », interdite sinon.
     */
    public static Map<String, String> erreurs(String categorie, String precision) {
        Map<String, String> erreurs = new LinkedHashMap<>();
        if (nettoyer(categorie) == null) {
            erreurs.put("categorie", MESSAGE_OBLIGATOIRE);
            return erreurs;
        }
        Optional<String> matiere = canonique(categorie);
        if (matiere.isEmpty()) {
            erreurs.put("categorie", MESSAGE_INCONNUE);
            return erreurs;
        }
        String nette = nettoyer(precision);
        if (AUTRE.equals(matiere.get())) {
            if (nette == null || nette.length() < PRECISION_MIN || nette.length() > PRECISION_MAX) {
                erreurs.put("categoriePrecision", MESSAGE_PRECISION);
            }
        } else if (nette != null) {
            erreurs.put("categoriePrecision", MESSAGE_PRECISION_INTERDITE);
        }
        return erreurs;
    }

    /**
     * Précision tirée d'un ancien libellé pour « Autre » : 80 caractères au plus (coupée à la fin d'un mot si possible, sans couper
     * un caractère en deux), 2 au moins (« Non précisée » si l'ancien libellé est trop court).
     */
    public static String precisionDepuisAncienLibelle(String ancien) {
        String texte = nettoyer(ancien);
        if (texte == null || texte.length() < PRECISION_MIN) {
            return "Non précisée";
        }
        if (texte.length() <= PRECISION_MAX) {
            return texte;
        }
        int fin = PRECISION_MAX;
        if (Character.isHighSurrogate(texte.charAt(fin - 1))) {
            fin--;
        }
        String coupe = texte.substring(0, fin);
        int espace = coupe.lastIndexOf(' ');
        if (espace >= PRECISION_MAX / 2) {
            coupe = coupe.substring(0, espace);
        }
        return coupe.strip();
    }
}

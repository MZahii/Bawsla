package tn.esprit.bawsla.user.service;

import java.util.List;

/**
 * Valeurs proposées pour le parcours d'un apprenant. Les domaines sont les matières des cours (liste commune des
 * modules Cours et Quiz, sans « Autre ») : un domaine choisi correspond ainsi à la catégorie des cours recommandés.
 */
public final class ListesParcours {

    public static final List<String> DOMAINES = List.of("Informatique", "Mathématiques", "Physique-Chimie",
            "Sciences de la vie et de la Terre", "Histoire", "Géographie", "Langues", "Économie et gestion",
            "Psychologie", "Philosophie", "Arts");

    public static final List<String> OBJECTIFS = List.of("Trouver un premier emploi", "Évoluer dans mon poste",
            "Réussir mes études", "Lancer un projet personnel", "Simplement apprendre");

    private ListesParcours() {
    }
}

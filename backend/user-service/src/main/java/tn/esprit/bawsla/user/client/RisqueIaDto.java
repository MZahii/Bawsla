package tn.esprit.bawsla.user.client;

import java.util.List;

public record RisqueIaDto(List<Resultat> resultats, double seuil, boolean donneesSimulees) {

    /** niveau : faible, moyen ou eleve ; facteurs : au plus 3 raisons lisibles. */
    public record Resultat(double probabilite, String niveau, List<String> facteurs) {
    }
}

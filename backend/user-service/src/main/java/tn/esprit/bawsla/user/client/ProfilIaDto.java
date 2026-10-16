package tn.esprit.bawsla.user.client;

import java.util.List;

public record ProfilIaDto(List<Groupe> groupes, boolean donneesSimulees) {

    public record Groupe(int id, String nom, String description) {
    }
}

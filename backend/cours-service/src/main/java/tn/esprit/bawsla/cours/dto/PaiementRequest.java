package tn.esprit.bawsla.cours.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Carte de test du paiement simulé. Rien n'est envoyé à un prestataire ; le numéro et le cryptogramme ne sont jamais stockés
 * (seuls les 4 derniers chiffres le sont). {@code expiration} au format MM/AA.
 */
public record PaiementRequest(
        @NotBlank(message = "Le numéro de carte est obligatoire")
        @Pattern(regexp = "[0-9 ]{12,23}", message = "Le numéro de carte ne doit contenir que des chiffres")
        String numeroCarte,
        @NotBlank(message = "La date d'expiration est obligatoire")
        @Pattern(regexp = "(0[1-9]|1[0-2])/[0-9]{2}", message = "La date d'expiration doit être au format MM/AA")
        String expiration,
        @NotBlank(message = "Le cryptogramme est obligatoire")
        @Pattern(regexp = "[0-9]{3,4}", message = "Le cryptogramme comporte 3 ou 4 chiffres")
        String cryptogramme) {

    /** Masque tout sauf les 4 derniers chiffres si l'objet est un jour journalisé. */
    @Override
    public String toString() {
        String chiffres = numeroCarte == null ? "" : numeroCarte.replace(" ", "");
        return "PaiementRequest[carte=**** " + (chiffres.length() >= 4 ? chiffres.substring(chiffres.length() - 4) : "") + "]";
    }
}

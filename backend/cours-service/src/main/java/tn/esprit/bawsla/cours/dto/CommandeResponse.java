package tn.esprit.bawsla.cours.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.cours.entity.Commande;

/**
 * Achat d'un cours (paiement simulé). {@code coursTitre} est omis si le cours a été supprimé depuis. {@code simule} vaut toujours true :
 * aucune somme n'est réellement débitée.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandeResponse(Long id, String reference, Long etudiantId, Long coursId, String coursTitre, BigDecimal montant,
                               String devise, String carte, LocalDateTime datePaiement, boolean simule) {

    public static CommandeResponse from(Commande c, String coursTitre) {
        return new CommandeResponse(c.getId(), c.getReference(), c.getEtudiantId(), c.getCoursId(), coursTitre, c.getMontant(), "TND",
                c.getMarque() + " •••• " + c.getDerniersChiffres(), c.getDatePaiement().truncatedTo(ChronoUnit.SECONDS), true);
    }
}

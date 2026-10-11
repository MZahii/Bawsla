package tn.esprit.bawsla.cours.service;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import feign.FeignException;
import tn.esprit.bawsla.cours.client.AudioIaRequestDto;
import tn.esprit.bawsla.cours.client.FicheIaDto;
import tn.esprit.bawsla.cours.client.FicheIaRequestDto;
import tn.esprit.bawsla.cours.client.FideliteIaDto;
import tn.esprit.bawsla.cours.client.FideliteIaRequestDto;
import tn.esprit.bawsla.cours.client.IaClient;
import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.exception.BawslaException;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.common.exception.UnauthorizedException;

/**
 * Point d'accès unique à l'IA pour le module : traduit toute panne en exception du module
 * (BadRequestException ou ServiceUnavailableException), jamais en erreur 500 brute.
 */
@Component
public class IaGateway {

    private static final Logger log = LoggerFactory.getLogger(IaGateway.class);

    private final IaClient client;

    public IaGateway(IaClient client) {
        this.client = client;
    }

    public FicheIaDto fiche(String authorization, FicheIaRequestDto corps) {
        return donnees(appeler(() -> client.fiche(exiger(authorization), corps)));
    }

    public FideliteIaDto fidelite(String authorization, FideliteIaRequestDto corps) {
        return donnees(appeler(() -> client.fidelite(exiger(authorization), corps)));
    }

    public byte[] audio(String authorization, AudioIaRequestDto corps) {
        byte[] mp3 = appeler(() -> client.audio(exiger(authorization), corps));
        if (mp3 == null || mp3.length == 0) {
            throw new ServiceUnavailableException("La synthèse vocale n'a produit aucun son.");
        }
        return mp3;
    }

    private static String exiger(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("Session introuvable : reconnecte-toi puis réessaie.");
        }
        return authorization;
    }

    private static <T> T donnees(ApiResponse<T> reponse) {
        if (reponse == null || reponse.data() == null) {
            throw new ServiceUnavailableException("L'IA a renvoyé une réponse vide.");
        }
        return reponse.data();
    }

    private <T> T appeler(Supplier<T> appel) {
        try {
            return appel.get();
        } catch (BawslaException e) {
            throw e;
        } catch (FeignException e) {
            // status() = -1 : connexion impossible ou délai dépassé. On ne recopie pas le message technique.
            log.warn("Appel à l'IA en échec (statut {})", e.status());
            throw new ServiceUnavailableException(e.status() < 0
                    ? "Le service d'IA est injoignable pour le moment."
                    : "Le service d'IA a répondu une erreur (" + e.status() + ").");
        } catch (RuntimeException e) {
            log.warn("Appel à l'IA en échec : {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Le service d'IA est injoignable pour le moment.");
        }
    }
}

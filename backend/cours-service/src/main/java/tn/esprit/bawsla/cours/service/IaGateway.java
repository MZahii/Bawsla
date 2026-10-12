package tn.esprit.bawsla.cours.service;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /** Audio reçu de l'IA : octets, type MIME (audio/mpeg ou audio/wav) et nom du fournisseur vocal qui l'a produit. */
    public record AudioIa(byte[] octets, String mime, String fournisseur) {
    }

    static final String ENTETE_FOURNISSEUR = "X-TTS-Fournisseur";

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

    public AudioIa audio(String authorization, AudioIaRequestDto corps) {
        ResponseEntity<byte[]> reponse = appeler(() -> client.audio(exiger(authorization), corps));
        byte[] octets = reponse == null ? null : reponse.getBody();
        if (octets == null || octets.length == 0) {
            throw new ServiceUnavailableException("La synthèse vocale n'a produit aucun son.");
        }
        MediaType type = reponse.getHeaders().getContentType();
        String mime = type == null ? "audio/mpeg" : type.getType() + "/" + type.getSubtype();
        String fournisseur = reponse.getHeaders().getFirst(ENTETE_FOURNISSEUR);
        return new AudioIa(octets, mime, fournisseur == null || fournisseur.isBlank() ? null : fournisseur.strip());
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

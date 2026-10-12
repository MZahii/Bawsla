package tn.esprit.bawsla.cours.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import feign.FeignException;
import feign.Request;
import tn.esprit.bawsla.cours.client.AudioExplicationIaDto;
import tn.esprit.bawsla.cours.client.AudioExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.AudioIaRequestDto;
import tn.esprit.bawsla.cours.client.ExplicationIaDto;
import tn.esprit.bawsla.cours.client.ExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.FideliteExplicationIaDto;
import tn.esprit.bawsla.cours.client.FideliteExplicationIaRequestDto;
import tn.esprit.bawsla.cours.client.TraductionIaRequestDto;
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
    private final Request.Options delaiScript;
    private final Request.Options delaiAudio;
    private final Request.Options delaiControle;

    /**
     * Délais de lecture PAR APPEL (le client Feign garde son délai par défaut pour les autres appels).
     * Script et traduction : ai-service accorde 150 s au LLM et peut relancer une fois (2 x 150 s), d'où 330 s.
     * Audio : plusieurs appels de synthèse vocale, parfois avec repli sur un autre fournisseur. Contrôle de fidélité : sans LLM.
     */
    public IaGateway(IaClient client,
                     @Value("${cours.ia.delai-script-secondes:330}") int delaiScriptSecondes,
                     @Value("${cours.ia.delai-audio-secondes:420}") int delaiAudioSecondes,
                     @Value("${cours.ia.delai-controle-secondes:30}") int delaiControleSecondes) {
        this.client = client;
        this.delaiScript = delai(delaiScriptSecondes);
        this.delaiAudio = delai(delaiAudioSecondes);
        this.delaiControle = delai(delaiControleSecondes);
    }

    private static Request.Options delai(int lectureSecondes) {
        return new Request.Options(5, TimeUnit.SECONDS, lectureSecondes, TimeUnit.SECONDS, true);
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

    // ---- explication orale

    public ExplicationIaDto explication(String authorization, ExplicationIaRequestDto corps) {
        return donnees(appeler(() -> client.explication(exiger(authorization), corps, delaiScript)));
    }

    public ExplicationIaDto traduireExplication(String authorization, TraductionIaRequestDto corps) {
        return donnees(appeler(() -> client.traduireExplication(exiger(authorization), corps, delaiScript)));
    }

    public FideliteExplicationIaDto fideliteExplication(String authorization, FideliteExplicationIaRequestDto corps) {
        return donnees(appeler(() -> client.fideliteExplication(exiger(authorization), corps, delaiControle)));
    }

    public AudioExplicationIaDto audioExplication(String authorization, AudioExplicationIaRequestDto corps) {
        AudioExplicationIaDto audio = donnees(appeler(() -> client.audioExplication(exiger(authorization), corps, delaiAudio)));
        if (audio.audioBase64() == null || audio.audioBase64().isBlank()) {
            throw new ServiceUnavailableException("La synthèse vocale n'a produit aucun son.");
        }
        return audio;
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

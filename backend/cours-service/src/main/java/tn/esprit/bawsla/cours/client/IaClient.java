package tn.esprit.bawsla.cours.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import feign.Request;
import tn.esprit.bawsla.cours.common.ApiResponse;

/**
 * Appels à ai-service PAR la gateway (/api/ai/cours/**), comme l'exige CLAUDE.md.
 * La gateway valide le JWT et ne transmet pas les en-têtes X-User-* reçus : on lui renvoie donc le
 * {@code Authorization} de l'utilisateur, qui lui permet de reposer elle-même X-User-Id et X-User-Role.
 * "api-gateway" = nom Eureka ; {@code cours.ia.gateway-url} force une URL (tests, Docker).
 */
@FeignClient(name = "api-gateway", contextId = "iaClient", url = "${cours.ia.gateway-url:}", primary = false,
        configuration = IaFeignConfig.class)
public interface IaClient {

    @PostMapping("/api/ai/cours/fiche")
    ApiResponse<FicheIaDto> fiche(@RequestHeader("Authorization") String authorization, @RequestBody FicheIaRequestDto corps);

    @PostMapping("/api/ai/cours/fidelite")
    ApiResponse<FideliteIaDto> fidelite(@RequestHeader("Authorization") String authorization, @RequestBody FideliteIaRequestDto corps);

    // ---- Explication orale : appels longs. Le dernier paramètre (Request.Options) donne à CHAQUE appel son propre délai de
    // lecture, sans toucher au délai par défaut des autres appels (voir IaGateway).

    @PostMapping("/api/ai/cours/explication")
    ApiResponse<ExplicationIaDto> explication(@RequestHeader("Authorization") String authorization,
                                              @RequestBody ExplicationIaRequestDto corps, Request.Options delais);

    @PostMapping("/api/ai/cours/explication/traduire")
    ApiResponse<ExplicationIaDto> traduireExplication(@RequestHeader("Authorization") String authorization,
                                                      @RequestBody TraductionIaRequestDto corps, Request.Options delais);

    @PostMapping("/api/ai/cours/explication/fidelite")
    ApiResponse<FideliteExplicationIaDto> fideliteExplication(@RequestHeader("Authorization") String authorization,
                                                              @RequestBody FideliteExplicationIaRequestDto corps, Request.Options delais);

    @PostMapping("/api/ai/cours/explication/audio")
    ApiResponse<AudioExplicationIaDto> audioExplication(@RequestHeader("Authorization") String authorization,
                                                        @RequestBody AudioExplicationIaRequestDto corps, Request.Options delais);

    /** Résumé automatique local (modèle Hugging Face ou phrases clés) ; le premier appel charge le modèle, d'où le délai propre. */
    @PostMapping("/api/ai/cours/resume-local")
    ApiResponse<ResumeLocalIaDto> resumeLocal(@RequestHeader("Authorization") String authorization,
                                              @RequestBody ResumeLocalIaRequestDto corps, Request.Options delais);

    /**
     * Audio du texte, produit par le premier fournisseur vocal disponible de la chaîne (ElevenLabs, Gemini, gTTS, edge-tts).
     * Le type MIME est dans {@code Content-Type} et le fournisseur dans l'en-tête {@code X-TTS-Fournisseur}.
     */
    @PostMapping("/api/ai/cours/audio")
    ResponseEntity<byte[]> audio(@RequestHeader("Authorization") String authorization, @RequestBody AudioIaRequestDto corps);
}

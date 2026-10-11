package tn.esprit.bawsla.cours.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

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

    /** MP3 du texte. */
    @PostMapping("/api/ai/cours/audio")
    byte[] audio(@RequestHeader("Authorization") String authorization, @RequestBody AudioIaRequestDto corps);
}

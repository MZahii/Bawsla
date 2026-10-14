package tn.esprit.bawsla.quiz.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import tn.esprit.bawsla.quiz.common.ApiResponse;

/**
 * Appels à ai-service PAR la gateway (/api/ai/quiz/**), comme l'exige CLAUDE.md.
 * La gateway valide le JWT et ne transmet pas les en-têtes X-User-* reçus : on lui renvoie donc le
 * {@code Authorization} de l'utilisateur, qui lui permet de reposer elle-même X-User-Id et X-User-Role.
 * "api-gateway" = nom Eureka ; {@code quiz.ia.gateway-url} force une URL (tests, Docker).
 */
@FeignClient(name = "api-gateway", contextId = "iaClient", url = "${quiz.ia.gateway-url:}", primary = false,
        configuration = IaFeignConfig.class)
public interface IaClient {

    /** Diagnostic d'une mauvaise réponse et question de rattrapage. Aucune donnée de l'apprenant n'est envoyée. */
    @PostMapping("/api/ai/quiz/remedier")
    ApiResponse<RemedierIaDto> remedier(@RequestHeader("Authorization") String authorization,
                                        @RequestBody RemedierIaRequestDto corps);
}

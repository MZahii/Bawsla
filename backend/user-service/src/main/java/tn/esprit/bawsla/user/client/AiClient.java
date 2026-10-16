package tn.esprit.bawsla.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.fasterxml.jackson.databind.JsonNode;

import tn.esprit.bawsla.user.common.ApiResponse;

/**
 * ai-service (module User, contrat 5), joint PAR la gateway (/api/ai/user/**) comme l'exige CLAUDE.md.
 * La gateway valide le JWT et ignore les X-User-* reçus : on lui renvoie l'en-tête Authorization de l'utilisateur.
 * Seuls des indicateurs chiffrés et des cours partent vers l'IA : jamais de nom, d'email ni d'identifiant.
 * "api-gateway" = nom Eureka ; {@code user.ia.gateway-url} force une URL (tests).
 */
@FeignClient(name = "api-gateway", contextId = "aiClient", url = "${user.ia.gateway-url:}", primary = false,
        configuration = AiFeignConfig.class)
public interface AiClient {

    @PostMapping("/api/ai/user/profil")
    ApiResponse<ProfilIaDto> profil(@RequestHeader("Authorization") String authorization,
                                    @RequestBody LotIndicateursIaDto corps);

    @PostMapping("/api/ai/user/risque")
    ApiResponse<RisqueIaDto> risque(@RequestHeader("Authorization") String authorization,
                                    @RequestBody LotIndicateursIaDto corps);

    @PostMapping("/api/ai/user/recommandations")
    ApiResponse<RecommandationsIaDto> recommandations(@RequestHeader("Authorization") String authorization,
                                                      @RequestBody RecommandationsIaRequestDto corps);

    // ------------------------------------------------------------ IA générative (LLM) : le ML décide, le LLM explique

    @PostMapping("/api/ai/user/conseil")
    ApiResponse<JsonNode> conseil(@RequestHeader("Authorization") String authorization,
                                  @RequestBody IaGenerativeDto.Conseil corps);

    @PostMapping("/api/ai/user/explication-risque")
    ApiResponse<JsonNode> explicationRisque(@RequestHeader("Authorization") String authorization,
                                            @RequestBody IaGenerativeDto.ExplicationRisque corps);

    @PostMapping("/api/ai/user/plan")
    ApiResponse<JsonNode> plan(@RequestHeader("Authorization") String authorization,
                               @RequestBody IaGenerativeDto.Plan corps);

    @PostMapping("/api/ai/user/rapport-cohorte")
    ApiResponse<JsonNode> rapportCohorte(@RequestHeader("Authorization") String authorization,
                                         @RequestBody IaGenerativeDto.Cohorte corps);

    @PostMapping("/api/ai/user/domaines")
    ApiResponse<JsonNode> domaines(@RequestHeader("Authorization") String authorization,
                                   @RequestBody IaGenerativeDto.Domaines corps);
}

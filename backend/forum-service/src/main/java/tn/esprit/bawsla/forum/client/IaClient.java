package tn.esprit.bawsla.forum.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import tn.esprit.bawsla.forum.common.ApiResponse;

/**
 * Appels à ai-service PAR la gateway (/api/ai/forum/**), comme l'exige CLAUDE.md.
 * La gateway valide le JWT et ne transmet pas les en-têtes X-User-* reçus : on lui renvoie le {@code Authorization}
 * de l'utilisateur (voir IdentiteService), qui lui permet de reposer X-User-Id et X-User-Role.
 * "api-gateway" = nom Eureka ; {@code forum.ia.gateway-url} force une URL (tests, Docker).
 * Aucun identifiant d'utilisateur n'est envoyé dans les corps (le schéma de l'ai-service le refuserait).
 */
@FeignClient(name = "api-gateway", contextId = "forumIaClient", url = "${forum.ia.gateway-url:}", primary = false,
        configuration = IaFeignConfig.class)
public interface IaClient {

    @PostMapping("/api/ai/forum/vectoriser")
    ApiResponse<VectoriserIaDto.Reponse> vectoriser(@RequestHeader("Authorization") String authorization,
                                                    @RequestBody VectoriserIaDto.Requete corps);

    @PostMapping("/api/ai/forum/indexer")
    ApiResponse<IndexerIaDto.Reponse> indexer(@RequestHeader("Authorization") String authorization,
                                              @RequestBody IndexerIaDto.Requete corps);

    @PostMapping("/api/ai/forum/reformuler")
    ApiResponse<ReformulerIaDto.Reponse> reformuler(@RequestHeader("Authorization") String authorization,
                                                    @RequestBody ReformulerIaDto.Requete corps);

    @PostMapping("/api/ai/forum/repondre")
    ApiResponse<RepondreIaDto.Reponse> repondre(@RequestHeader("Authorization") String authorization,
                                                @RequestBody RepondreIaDto.Requete corps);

    @PostMapping("/api/ai/forum/moderer")
    ApiResponse<ModererIaDto.Reponse> moderer(@RequestHeader("Authorization") String authorization,
                                              @RequestBody ModererIaDto.Requete corps);

    @PostMapping("/api/ai/forum/repondre-general")
    ApiResponse<RepondreIaDto.ReponseGenerale> repondreGeneral(@RequestHeader("Authorization") String authorization,
                                                               @RequestBody RepondreIaDto.RequeteGenerale corps);
}

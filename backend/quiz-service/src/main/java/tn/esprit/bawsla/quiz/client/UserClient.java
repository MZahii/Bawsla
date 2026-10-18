package tn.esprit.bawsla.quiz.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import tn.esprit.bawsla.quiz.common.ApiResponse;

/**
 * Contrat 4 : GET /api/users/{id} sur user-service (nom Eureka), sans passer par la gateway.
 * {@link FeignHeadersInterceptor} propage X-User-Id : user-service renvoie alors la vue publique de tout compte,
 * formateur en attente compris (sans cet en-tête, il ne répond que pour les formateurs validés).
 */
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/api/users/{id}")
    ApiResponse<UserDto> getUser(@PathVariable("id") Long id);
}

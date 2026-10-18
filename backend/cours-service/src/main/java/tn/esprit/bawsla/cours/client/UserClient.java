package tn.esprit.bawsla.cours.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import tn.esprit.bawsla.cours.common.ApiResponse;

/**
 * Contrat 4 : GET /api/users/{id} sur user-service (nom Eureka), sans passer par la gateway.
 * X-User-Id est transmis pour obtenir la vue publique de n'importe quel compte (formateur en attente compris) ;
 * sans lui, user-service ne renvoie que les formateurs validés et répond 404 pour les autres.
 */
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/api/users/{id}")
    ApiResponse<UserDto> getUser(@PathVariable("id") Long id, @RequestHeader("X-User-Id") Long demandeurId);
}

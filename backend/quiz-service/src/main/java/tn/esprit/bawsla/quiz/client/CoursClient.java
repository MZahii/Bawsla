package tn.esprit.bawsla.quiz.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import tn.esprit.bawsla.quiz.common.ApiResponse;

/**
 * EXEMPLE DE RÉFÉRENCE d'un client inter-services.
 * "cours-service" = nom Eureka (spring.application.name) : l'appel est direct (sans gateway),
 * les en-têtes X-User-* sont propagés par {@link FeignHeadersInterceptor}.
 * Contrat : GET /api/cours/{id} (voir CONTRATS_API.md).
 */
@FeignClient(name = "cours-service")
public interface CoursClient {

    @GetMapping("/api/cours/{id}")
    ApiResponse<CoursDto> getCours(@PathVariable("id") Long id);
}

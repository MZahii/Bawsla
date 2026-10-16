package tn.esprit.bawsla.forum.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import tn.esprit.bawsla.forum.common.ApiResponse;

/**
 * cours-service, résolu par Eureka ; les en-têtes X-User-* sont propagés par {@link FeignHeadersInterceptor}.
 * Le forum ne fait que LIRE des routes existantes et se dégrade proprement si elles ne répondent pas
 * (voir AnnuaireService) : aucun développement demandé au module Cours.
 */
@FeignClient(name = "cours-service", contextId = "forumCoursClient")
public interface CoursClient {

    /** Contrat 1 : titre et formateur du cours ; 404 = cours absent ou invisible pour l'utilisateur. */
    @GetMapping("/api/cours/{id}")
    ApiResponse<CoursDto> getCours(@PathVariable("id") Long id);

    /** Contrat 2 : texte brut du cours (stub tant que l'extraction du PDF n'existe pas). */
    @GetMapping("/api/cours/{id}/texte")
    ApiResponse<CoursTexteDto> getTexte(@PathVariable("id") Long id);

    /** Chapitres du cours, lus seulement si la route existe ; sinon le texte de démonstration prend le relais. */
    @GetMapping("/api/cours/{id}/chapitres")
    ApiResponse<List<ChapitreDto>> getChapitres(@PathVariable("id") Long id);
}

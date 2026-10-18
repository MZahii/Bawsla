package tn.esprit.bawsla.user.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import tn.esprit.bawsla.user.common.ApiResponse;

/** cours-service, appelé directement par Eureka (X-User-* propagés par FeignHeadersInterceptor). */
@FeignClient(name = "cours-service")
public interface CoursClient {

    /** Contrat 7 : inscriptions d'un apprenant. */
    @GetMapping("/api/cours/inscriptions")
    ApiResponse<List<InscriptionDto>> inscriptions(@RequestParam("etudiantId") Long etudiantId);

    /** Contrat 7 : inscrits d'un cours (formateur propriétaire ou admin). */
    @GetMapping("/api/cours/{id}/inscriptions")
    ApiResponse<List<InscriptionDto>> inscriptionsDuCours(@PathVariable("id") Long coursId);

    /** Contrat 9 : catalogue visible par l'utilisateur courant (un apprenant ne voit que les cours publiés). */
    @GetMapping("/api/cours")
    ApiResponse<List<CoursDto>> catalogue();
}

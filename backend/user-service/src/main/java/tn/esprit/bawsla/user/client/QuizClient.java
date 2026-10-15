package tn.esprit.bawsla.user.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import tn.esprit.bawsla.user.common.ApiResponse;

/** quiz-service, appelé directement par Eureka (X-User-* propagés par FeignHeadersInterceptor). */
@FeignClient(name = "quiz-service")
public interface QuizClient {

    /** Contrat 3 (en place) : notions ratées, agrégées par compétence. */
    @GetMapping("/api/quiz/notions-ratees")
    ApiResponse<List<NotionRateeDto>> notionsRatees(@RequestParam("etudiantId") Long etudiantId);

    /**
     * Ses propres tentatives (route existante de quiz-service, utilisateur courant). Sert de repli pour l'apprenant
     * lui-même tant que le contrat 6 n'existe pas ; ne donne ni nbCorrectes ni nbQuestions.
     */
    @GetMapping("/api/quiz/tentatives/mes")
    ApiResponse<List<HistoriqueTentativeDto>> mesTentatives();

    /** Contrat 6 (proposé) : tentatives corrigées d'un apprenant. 404 tant que la route n'existe pas. */
    @GetMapping("/api/quiz/tentatives")
    ApiResponse<List<TentativeDto>> tentatives(@RequestParam("etudiantId") Long etudiantId);
}

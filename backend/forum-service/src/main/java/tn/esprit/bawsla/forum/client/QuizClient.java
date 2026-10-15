package tn.esprit.bawsla.forum.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import tn.esprit.bawsla.forum.common.ApiResponse;

/** quiz-service, contrat 3 : notions ratées d'un apprenant (section « Pour toi », masquée si indisponible). */
@FeignClient(name = "quiz-service", contextId = "forumQuizClient")
public interface QuizClient {

    @GetMapping("/api/quiz/notions-ratees")
    ApiResponse<List<NotionRateeDto>> notionsRatees(@RequestParam("etudiantId") Long etudiantId);
}

package tn.esprit.bawsla.quiz.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.quiz.common.ApiResponse;

/** Endpoint de santé public (via la gateway : GET /api/quiz/health). */
@RestController
public class HealthController {

    @GetMapping("/api/quiz/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("service", "quiz-service", "status", "UP"));
    }
}

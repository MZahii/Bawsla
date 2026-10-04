package tn.esprit.bawsla.cours.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.cours.common.ApiResponse;

/** Endpoint de santé public (via la gateway : GET /api/cours/health). */
@RestController
public class HealthController {

    @GetMapping("/api/cours/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("service", "cours-service", "status", "UP"));
    }
}

package tn.esprit.bawsla.user.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.user.common.ApiResponse;

/** Endpoint de santé public (via la gateway : GET /api/users/health). */
@RestController
public class HealthController {

    @GetMapping("/api/users/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("service", "user-service", "status", "UP"));
    }
}

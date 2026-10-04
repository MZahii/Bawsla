package tn.esprit.bawsla.forum.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.forum.common.ApiResponse;

/** Endpoint de santé public (via la gateway : GET /api/forum/health). */
@RestController
public class HealthController {

    @GetMapping("/api/forum/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("service", "forum-service", "status", "UP"));
    }
}

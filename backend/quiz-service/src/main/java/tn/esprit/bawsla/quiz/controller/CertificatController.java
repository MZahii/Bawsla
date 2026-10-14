package tn.esprit.bawsla.quiz.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.quiz.common.ApiResponse;
import tn.esprit.bawsla.quiz.common.security.CurrentUser;
import tn.esprit.bawsla.quiz.dto.CertificatResponse;
import tn.esprit.bawsla.quiz.service.CertificatService;

@RestController
@RequestMapping("/api/quiz/certificats")
public class CertificatController {

    private final CertificatService certificatService;

    public CertificatController(CertificatService certificatService) {
        this.certificatService = certificatService;
    }

    @GetMapping("/mes")
    public ApiResponse<List<CertificatResponse>> mesCertificats(CurrentUser user) {
        return ApiResponse.ok(certificatService.mesCertificats(user));
    }

    @GetMapping
    public ApiResponse<List<CertificatResponse>> tous(CurrentUser user) {
        return ApiResponse.ok(certificatService.tous(user));
    }

    @GetMapping("/{numero}")
    public ApiResponse<CertificatResponse> verifier(@PathVariable String numero) {
        return ApiResponse.ok(certificatService.verifier(numero));
    }
}

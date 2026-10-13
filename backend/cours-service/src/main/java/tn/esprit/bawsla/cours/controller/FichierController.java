package tn.esprit.bawsla.cours.controller;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CoursResponse;
import tn.esprit.bawsla.cours.service.FichierService;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;

/** Image de couverture et PDF d'un cours. L'envoi se fait en multipart/form-data, champ « fichier ». */
@RestController
@RequestMapping("/api/cours/{coursId}")
public class FichierController {

    private final FichierService fichierService;

    public FichierController(FichierService fichierService) {
        this.fichierService = fichierService;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoursResponse> deposerImage(@PathVariable Long coursId,
                                                   @RequestParam(value = "fichier", required = false) MultipartFile fichier,
                                                   CurrentUser user) {
        return ApiResponse.ok(fichierService.deposerImage(coursId, fichier, user), "Image enregistrée");
    }

    /**
     * Image d'un cours publié, lisible même sans en-têtes d'identité (visiteur) si la gateway l'autorise un jour.
     * L'URL contient la version du fichier (?v=), donc le navigateur peut la garder en cache.
     */
    @GetMapping("/image")
    public ResponseEntity<Resource> image(@PathVariable Long coursId,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        CurrentUser user = userId == null || role == null ? null : new CurrentUser(userId, role);
        Fichier f = fichierService.image(coursId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.ressource());
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoursResponse> deposerPdf(@PathVariable Long coursId,
                                                 @RequestParam(value = "fichier", required = false) MultipartFile fichier,
                                                 CurrentUser user) {
        return ApiResponse.ok(fichierService.deposerPdf(coursId, fichier, user), "PDF enregistré");
    }

    @GetMapping("/pdf")
    public ResponseEntity<Resource> pdf(@PathVariable Long coursId, CurrentUser user) {
        Fichier f = fichierService.pdf(coursId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(f.nom(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.ressource());
    }
}

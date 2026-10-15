package tn.esprit.bawsla.cours.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CorrectionPageRequest;
import tn.esprit.bawsla.cours.dto.PageLueResponse;
import tn.esprit.bawsla.cours.dto.PagesLuesResponse;
import tn.esprit.bawsla.cours.dto.TraitementResponse;
import tn.esprit.bawsla.cours.service.FichierService.Fichier;
import tn.esprit.bawsla.cours.service.PagesLuesService;

/**
 * Pages lues par l'IA (vision) : PDF scanné et images déposées par le formateur. Toutes les routes sont réservées au propriétaire du cours
 * et à l'ADMIN. Le {@code Authorization} est lu ICI, avant tout passage en asynchrone, puis transmis à l'IA par la gateway.
 */
@RestController
@RequestMapping("/api/cours/{coursId}")
public class PagesController {

    private final PagesLuesService service;

    public PagesController(PagesLuesService service) {
        this.service = service;
    }

    /** Pages lues par l'IA ou sans texte, avec le nombre de pages encore à lire. */
    @GetMapping("/pages")
    public ApiResponse<PagesLuesResponse> pages(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(service.lister(coursId, user));
    }

    /** Dépose une image de page (PNG ou JPEG, 5 Mo) : elle devient la page suivante du cours. */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PageLueResponse> deposerImage(@PathVariable Long coursId, @RequestPart("fichier") MultipartFile fichier, CurrentUser user) {
        return ApiResponse.ok(service.deposerImage(coursId, fichier, user), "Image ajoutée");
    }

    /** Lance la lecture par l'IA des pages vides (30 au plus par lancement). Suivre avec GET /pages/traitement. */
    @PostMapping("/pages/lire")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<TraitementResponse> lire(@PathVariable Long coursId, CurrentUser user,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.lire(coursId, user, authorization), "Lecture lancée");
    }

    @GetMapping("/pages/traitement")
    public ApiResponse<TraitementResponse> traitement(@PathVariable Long coursId, CurrentUser user) {
        return ApiResponse.ok(service.traitement(coursId, user));
    }

    /** Corrige le texte d'une page. */
    @PutMapping("/pages/{numero}")
    public ApiResponse<PageLueResponse> corriger(@PathVariable Long coursId, @PathVariable int numero,
                                                 @Valid @RequestBody CorrectionPageRequest requete, CurrentUser user) {
        return ApiResponse.ok(service.corriger(coursId, numero, requete.texte(), user), "Page enregistrée");
    }

    /** Supprime une image déposée (les pages suivantes sont renumérotées). */
    @DeleteMapping("/pages/{numero}")
    public ApiResponse<Void> supprimer(@PathVariable Long coursId, @PathVariable int numero, CurrentUser user) {
        service.supprimerImage(coursId, numero, user);
        return ApiResponse.ok(null, "Image supprimée");
    }

    /** Aperçu de l'image déposée, pour la comparer au texte lu. */
    @GetMapping("/pages/{numero}/image")
    public ResponseEntity<Resource> image(@PathVariable Long coursId, @PathVariable int numero, CurrentUser user) {
        Fichier f = service.image(coursId, numero, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .header("X-Content-Type-Options", "nosniff")
                .body(f.ressource());
    }
}

package tn.esprit.bawsla.forum.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.EtiquetteResponse;
import tn.esprit.bawsla.forum.dto.PieceJointeResponse;
import tn.esprit.bawsla.forum.service.EtiquetteService;
import tn.esprit.bawsla.forum.service.PieceJointeService;
import tn.esprit.bawsla.forum.service.PieceJointeService.Contenu;

/** Autocomplétion des étiquettes et images jointes aux messages. */
@RestController
@RequestMapping("/api/forum")
public class EtiquetteController {

    private final EtiquetteService etiquettes;
    private final PieceJointeService piecesJointes;

    public EtiquetteController(EtiquetteService etiquettes, PieceJointeService piecesJointes) {
        this.etiquettes = etiquettes;
        this.piecesJointes = piecesJointes;
    }

    @GetMapping("/etiquettes")
    public ApiResponse<List<EtiquetteResponse>> etiquettes(@RequestParam(required = false) Long coursId,
                                                           @RequestParam(required = false) String q,
                                                           CurrentUser user) {
        return ApiResponse.ok(etiquettes.rechercher(coursId, q, user));
    }

    @PostMapping("/pieces-jointes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PieceJointeResponse> televerser(@RequestPart("fichier") MultipartFile fichier,
                                                       @RequestParam(required = false) Long discussionId,
                                                       @RequestParam(required = false) Long reponseId,
                                                       CurrentUser user) {
        return ApiResponse.ok(piecesJointes.televerser(fichier, discussionId, reponseId, user), "Image ajoutée");
    }

    /** Sert l'image avec son type vérifié ; nosniff empêche le navigateur de l'interpréter autrement. */
    @GetMapping("/pieces-jointes/{id}")
    public ResponseEntity<byte[]> lire(@PathVariable Long id, CurrentUser user) {
        Contenu contenu = piecesJointes.lire(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contenu.type()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(contenu.octets());
    }
}

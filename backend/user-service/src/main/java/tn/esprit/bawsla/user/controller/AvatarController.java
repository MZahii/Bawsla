package tn.esprit.bawsla.user.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.entity.Avatar;
import tn.esprit.bawsla.user.service.AvatarService;

/** Photo de profil. Le front la charge en blob (la gateway exige le JWT, impossible avec un simple img src). */
@RestController
@RequestMapping("/api/users")
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    /** multipart/form-data, champ « fichier ». */
    @PostMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserDto> enregistrer(@RequestParam(name = "fichier", required = false) MultipartFile fichier,
                                            CurrentUser user) {
        return ApiResponse.ok(avatarService.enregistrer(user, fichier), "Photo enregistrée");
    }

    @DeleteMapping("/me/avatar")
    public ApiResponse<UserDto> supprimer(CurrentUser user) {
        return ApiResponse.ok(avatarService.supprimer(user), "Photo supprimée");
    }

    /** L'image brute. Le front ajoute ?v=avatarVersion : une nouvelle photo change d'URL, d'où le cache long. */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> lire(@PathVariable Long id, CurrentUser user) {
        Avatar avatar = avatarService.lire(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.getTypeMime()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.getContenu());
    }
}

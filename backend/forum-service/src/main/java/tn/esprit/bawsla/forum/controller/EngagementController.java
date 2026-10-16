package tn.esprit.bawsla.forum.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.ActiviteResponse;
import tn.esprit.bawsla.forum.dto.ClassementEntree;
import tn.esprit.bawsla.forum.dto.CompteurResponse;
import tn.esprit.bawsla.forum.dto.NotificationResponse;
import tn.esprit.bawsla.forum.dto.PageResponse;
import tn.esprit.bawsla.forum.dto.ProfilResponse;
import tn.esprit.bawsla.forum.dto.ReputationResponse;
import tn.esprit.bawsla.forum.dto.StatistiquesResponse;
import tn.esprit.bawsla.forum.service.NotificationService;
import tn.esprit.bawsla.forum.service.ProfilService;
import tn.esprit.bawsla.forum.service.StatistiquesService;
import tn.esprit.bawsla.forum.service.SuiviService;
import tn.esprit.bawsla.forum.service.SuiviService.EtatSuivi;

/** Suivis, favoris, lu / non lu, notifications, profil, réputation, classement et statistiques. */
@RestController
@RequestMapping("/api/forum")
public class EngagementController {

    private final SuiviService suivis;
    private final NotificationService notifications;
    private final ProfilService profils;
    private final StatistiquesService statistiques;

    public EngagementController(SuiviService suivis, NotificationService notifications, ProfilService profils,
                                StatistiquesService statistiques) {
        this.suivis = suivis;
        this.notifications = notifications;
        this.profils = profils;
        this.statistiques = statistiques;
    }

    // ---------------------------------------------------------------- Suivis, favoris, lecture

    @PostMapping("/discussions/{id}/suivre")
    public ApiResponse<EtatSuivi> suivre(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(suivis.suivre(id, true, user), "Discussion suivie");
    }

    @DeleteMapping("/discussions/{id}/suivre")
    public ApiResponse<EtatSuivi> nePlusSuivre(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(suivis.suivre(id, false, user), "Discussion plus suivie");
    }

    @PostMapping("/discussions/{id}/favori")
    public ApiResponse<EtatSuivi> ajouterFavori(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(suivis.favori(id, true, user), "Ajoutée aux favoris");
    }

    @DeleteMapping("/discussions/{id}/favori")
    public ApiResponse<EtatSuivi> retirerFavori(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(suivis.favori(id, false, user), "Retirée des favoris");
    }

    @PostMapping("/discussions/lu")
    public ApiResponse<Integer> toutMarquerLu(@RequestParam(required = false) Long coursId, CurrentUser user) {
        return ApiResponse.ok(suivis.toutMarquerLu(coursId, user), "Tout est marqué comme lu");
    }

    /** « Ça répond à ma question » depuis les questions similaires (badge Éclaireur, suivi du fil). */
    @PostMapping("/discussions/{id}/reponse-trouvee")
    public ApiResponse<Void> reponseTrouvee(@PathVariable Long id, CurrentUser user) {
        suivis.reponseTrouvee(id, user);
        return ApiResponse.ok(null, "Content que tu aies trouvé ta réponse !");
    }

    // ---------------------------------------------------------------- Notifications

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<NotificationResponse>> notifications(
            @RequestParam(defaultValue = "false") boolean nonLues, @RequestParam(required = false) Integer page,
            CurrentUser user) {
        return ApiResponse.ok(notifications.lister(user, nonLues, page));
    }

    @GetMapping("/notifications/compteur")
    public ApiResponse<CompteurResponse> compteur(CurrentUser user) {
        return ApiResponse.ok(notifications.compteur(user));
    }

    /** Corps facultatif : liste d'identifiants ; absent ou vide = tout marquer comme lu. */
    @PostMapping("/notifications/lues")
    public ApiResponse<CompteurResponse> marquerLues(@RequestBody(required = false) List<Long> ids,
                                                     CurrentUser user) {
        return ApiResponse.ok(notifications.marquerLues(user, ids));
    }

    // ---------------------------------------------------------------- Profil, réputation, classement

    @GetMapping("/profils/{userId}")
    public ApiResponse<ProfilResponse> profil(@PathVariable Long userId, CurrentUser user) {
        return ApiResponse.ok(profils.profil(userId, user));
    }

    @GetMapping("/moi/activite")
    public ApiResponse<ActiviteResponse> activite(CurrentUser user) {
        return ApiResponse.ok(profils.activite(user));
    }

    @GetMapping("/moi/reputation")
    public ApiResponse<ReputationResponse> reputation(CurrentUser user) {
        return ApiResponse.ok(profils.reputation(user));
    }

    @GetMapping("/classement")
    public ApiResponse<List<ClassementEntree>> classement(@RequestParam(required = false) Long coursId,
                                                          @RequestParam(required = false) String periode,
                                                          CurrentUser user) {
        return ApiResponse.ok(profils.classement(coursId, periode, user));
    }

    // ---------------------------------------------------------------- Statistiques

    @GetMapping("/statistiques")
    public ApiResponse<StatistiquesResponse> statistiques(@RequestParam(required = false) Long coursId,
                                                          @RequestParam(required = false) Integer periode,
                                                          CurrentUser user) {
        return ApiResponse.ok(statistiques.statistiques(coursId, periode, user));
    }
}

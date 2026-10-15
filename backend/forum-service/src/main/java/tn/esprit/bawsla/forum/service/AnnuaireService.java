package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import feign.FeignException;
import tn.esprit.bawsla.forum.client.CoursClient;
import tn.esprit.bawsla.forum.client.CoursDto;
import tn.esprit.bawsla.forum.client.UserClient;
import tn.esprit.bawsla.forum.client.UserDto;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.service.CacheTemporaire.Calcul;

/**
 * Lecture (avec cache de 5 minutes) des cours et des membres dans les autres services.
 * Dégradation propre (plan §4.8) : si un service ne répond pas, on renvoie « Cours n° X » ou « Membre n° X »
 * marqué non joignable, jamais une erreur. Seul un 404 de cours-service signifie « cours absent ou invisible ».
 */
@Service
public class AnnuaireService {

    private static final Logger log = LoggerFactory.getLogger(AnnuaireService.class);
    private static final Duration DUREE = Duration.ofMinutes(5);
    private static final Duration DUREE_DEGRADEE = Duration.ofSeconds(30);

    /** Cours vu par le forum ; enseignantId est null quand cours-service n'a pas répondu. */
    public record CoursInfo(Long id, String titre, Long enseignantId, boolean joignable) {
    }

    /** Membre vu par le forum ; nom = « Prénom Nom », ou « Membre n° X » si user-service n'a pas répondu. */
    public record MembreInfo(Long id, String nom, String role, boolean joignable) {
    }

    private final CoursClient coursClient;
    private final UserClient userClient;
    private final CacheTemporaire<Long, Optional<CoursInfo>> cours;
    private final CacheTemporaire<Long, MembreInfo> membres;

    public AnnuaireService(CoursClient coursClient, UserClient userClient, Clock horloge) {
        this.coursClient = coursClient;
        this.userClient = userClient;
        this.cours = new CacheTemporaire<>(horloge);
        this.membres = new CacheTemporaire<>(horloge);
    }

    /** Vide = le cours n'existe pas ou n'est pas visible (404 de cours-service). */
    public Optional<CoursInfo> cours(Long coursId) {
        return cours.obtenir(coursId, this::chargerCours);
    }

    public MembreInfo membre(Long userId) {
        return membres.obtenir(userId, this::chargerMembre);
    }

    public void oublierCaches() {
        cours.vider();
        membres.vider();
    }

    private Calcul<Optional<CoursInfo>> chargerCours(Long id) {
        try {
            ApiResponse<CoursDto> reponse = coursClient.getCours(id);
            CoursDto dto = reponse == null ? null : reponse.data();
            if (dto == null) {
                return new Calcul<>(Optional.of(coursDegrade(id)), DUREE_DEGRADEE);
            }
            String titre = dto.titre() == null ? "Cours n° " + id : dto.titre();
            return new Calcul<>(Optional.of(new CoursInfo(id, titre, dto.enseignantId(), true)), DUREE);
        } catch (FeignException.NotFound e) {
            return new Calcul<>(Optional.empty(), DUREE_DEGRADEE);
        } catch (RuntimeException e) {
            log.warn("cours-service injoignable pour le cours {} : {}", id, e.getMessage());
            return new Calcul<>(Optional.of(coursDegrade(id)), DUREE_DEGRADEE);
        }
    }

    private Calcul<MembreInfo> chargerMembre(Long id) {
        try {
            ApiResponse<UserDto> reponse = userClient.getUser(id);
            UserDto dto = reponse == null ? null : reponse.data();
            if (dto == null) {
                return new Calcul<>(membreDegrade(id), DUREE_DEGRADEE);
            }
            String nom = String.join(" ", nonNul(dto.prenom()), nonNul(dto.nom())).trim();
            return new Calcul<>(new MembreInfo(id, nom.isEmpty() ? "Membre n° " + id : nom, dto.role(), true), DUREE);
        } catch (RuntimeException e) {
            log.warn("user-service injoignable pour le membre {} : {}", id, e.getMessage());
            return new Calcul<>(membreDegrade(id), DUREE_DEGRADEE);
        }
    }

    private static CoursInfo coursDegrade(Long id) {
        return new CoursInfo(id, "Cours n° " + id, null, false);
    }

    private static MembreInfo membreDegrade(Long id) {
        return new MembreInfo(id, "Membre n° " + id, null, false);
    }

    private static String nonNul(String s) {
        return s == null ? "" : s;
    }
}

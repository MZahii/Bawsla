package tn.esprit.bawsla.cours.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import feign.FeignException;
import tn.esprit.bawsla.cours.client.UserClient;
import tn.esprit.bawsla.cours.client.UserDto;
import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.exception.ForbiddenException;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;

/**
 * Contrat 4 : seul un formateur validé par l'admin publie. L'admin publie toujours.
 * {@code cours.publication.verifier-formateur=false} coupe l'appel (tests d'API sans user-service).
 */
@Component
public class FormateurVerifier {

    static final String NON_VALIDE =
            "Ton compte formateur n'est pas encore validé par l'administration : tu pourras publier dès sa validation.";

    private final UserClient userClient;
    private final boolean actif;

    public FormateurVerifier(UserClient userClient,
                             @Value("${cours.publication.verifier-formateur:true}") boolean actif) {
        this.userClient = userClient;
        this.actif = actif;
    }

    public void verifierPeutPublier(CurrentUser user) {
        if (!actif || user.isAdmin()) {
            return;
        }
        UserDto formateur;
        try {
            ApiResponse<UserDto> reponse = userClient.getUser(user.id(), user.id());
            formateur = reponse == null ? null : reponse.data();
        } catch (FeignException.NotFound e) {
            throw new ForbiddenException(NON_VALIDE);
        } catch (FeignException e) {
            throw new ServiceUnavailableException("user-service indisponible, réessaie plus tard");
        }
        if (formateur == null || !formateur.formateurValide()) {
            throw new ForbiddenException(NON_VALIDE);
        }
    }
}

package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import feign.FeignException;
import tn.esprit.bawsla.cours.client.UserClient;
import tn.esprit.bawsla.cours.client.UserDto;
import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.exception.ForbiddenException;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.service.FormateurVerifier;

/** Contrat 4 : seul un formateur validé par l'admin publie un cours. */
class FormateurVerifierTest {

    private static final CurrentUser FORMATEUR = new CurrentUser(10L, CurrentUser.ENSEIGNANT);

    private final UserClient userClient = mock(UserClient.class);
    private final FormateurVerifier verifier = new FormateurVerifier(userClient, true);

    @Test
    void unFormateurValidePublie() {
        when(userClient.getUser(10L, 10L)).thenReturn(ApiResponse.ok(new UserDto(10L, "ENSEIGNANT", true)));
        assertThatCode(() -> verifier.verifierPeutPublier(FORMATEUR)).doesNotThrowAnyException();
    }

    @Test
    void unFormateurEnAttenteNePubliePas() {
        when(userClient.getUser(10L, 10L)).thenReturn(ApiResponse.ok(new UserDto(10L, "ENSEIGNANT", false)));
        assertThatThrownBy(() -> verifier.verifierPeutPublier(FORMATEUR)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unCompteInconnuNePubliePas() {
        when(userClient.getUser(10L, 10L)).thenThrow(mock(FeignException.NotFound.class));
        assertThatThrownBy(() -> verifier.verifierPeutPublier(FORMATEUR)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userServiceEnPanneDonneUn503() {
        when(userClient.getUser(10L, 10L)).thenThrow(mock(FeignException.class));
        assertThatThrownBy(() -> verifier.verifierPeutPublier(FORMATEUR)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void lAdminPublieSansAppelAUserService() {
        verifier.verifierPeutPublier(new CurrentUser(1L, CurrentUser.ADMIN));
        verify(userClient, never()).getUser(anyLong(), anyLong());
    }
}

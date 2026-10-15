package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tn.esprit.bawsla.cours.client.FicheIaRequestDto;
import tn.esprit.bawsla.cours.client.IaInjoignableException;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.service.IaGateway;

/** Connexion refusée par l'IA (aucun service n'écoute) : message « injoignable » et code IA_INJOIGNABLE, pas un délai. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cours_ia_injoignable;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class IaInjoignableTest {

    @Autowired IaGateway ia;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("cours.ia.gateway-url", () -> "http://localhost:" + portFerme());
        registry.add("cours.stockage.dossier", () -> System.getProperty("java.io.tmpdir") + "/bawsla-ia-injoignable-test");
    }

    @Test
    void connexionRefuseeDonneIaInjoignable() {
        assertThatThrownBy(() -> ia.fiche("Bearer t", new FicheIaRequestDto(1L, "Texte du cours", Niveau.DEBUTANT)))
                .isInstanceOf(IaInjoignableException.class)
                .hasMessage("Le service d'IA est injoignable");
    }

    private static int portFerme() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

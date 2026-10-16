package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tn.esprit.bawsla.cours.client.FicheIaRequestDto;
import tn.esprit.bawsla.cours.client.IaInjoignableException;
import tn.esprit.bawsla.cours.client.IaTimeoutException;
import tn.esprit.bawsla.cours.controller.IaExceptionHandler;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.service.IaGateway;

/**
 * Délais de l'IA : le vrai client Feign contre un faux serveur lent, ou sur un port fermé. Un délai dépassé et une connexion refusée donnent
 * deux messages et deux codes distincts dans ApiError (IA_TIMEOUT et IA_INJOIGNABLE), avec un statut HTTP 503 dans les deux cas.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cours_ia_delai;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class IaDelaiTest {

    private static final HttpServer SERVEUR = demarrerServeur();
    private static final AtomicReference<String> COMPORTEMENT = new AtomicReference<>("lent");

    @Autowired IaGateway ia;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("cours.ia.gateway-url", () -> "http://localhost:" + SERVEUR.getAddress().getPort());
        registry.add("cours.ia.delai-fiche-secondes", () -> "1"); // lecture de la fiche limitée à 1 s dans ce test
        registry.add("cours.stockage.dossier", () -> System.getProperty("java.io.tmpdir") + "/bawsla-ia-delai-test");
    }

    @BeforeEach
    void reinitialiser() {
        COMPORTEMENT.set("lent");
    }

    @AfterAll
    static void arreter() {
        SERVEUR.stop(0);
    }

    private static FicheIaRequestDto fiche() {
        return new FicheIaRequestDto(1L, "Texte du cours", Niveau.DEBUTANT);
    }

    @Test
    void delaiDeLectureDepasseDonneIaTimeout() {
        long debut = System.currentTimeMillis();
        assertThatThrownBy(() -> ia.fiche("Bearer t", fiche()))
                .isInstanceOf(IaTimeoutException.class)
                .hasMessage("L'IA met trop de temps à répondre, réessaie dans un instant");
        assertThat(System.currentTimeMillis() - debut).isLessThan(2_500); // le délai propre de la fiche (1 s ici) s'applique bien
    }

    @Test
    void reponse504DeLIADonneIaTimeoutAvecSonMessage() {
        COMPORTEMENT.set("504");
        assertThatThrownBy(() -> ia.fiche("Bearer t", fiche()))
                .isInstanceOf(IaTimeoutException.class).hasMessage("Le fournisseur LLM n'a pas répondu à temps");
        COMPORTEMENT.set("504-vide");
        assertThatThrownBy(() -> ia.fiche("Bearer t", fiche())).isInstanceOf(IaTimeoutException.class).hasMessage(IaTimeoutException.MESSAGE);
    }

    @Test
    void autresErreursRestentDesIndisponibilitesOrdinaires() {
        COMPORTEMENT.set("503");
        assertThatThrownBy(() -> ia.fiche("Bearer t", fiche()))
                .hasMessageContaining("indisponible").isNotInstanceOf(IaTimeoutException.class).isNotInstanceOf(IaInjoignableException.class);
    }

    @Test
    void classificationDesPannesSansReponse() {
        assertThat(IaGateway.sansReponse(new RuntimeException("x", new java.net.SocketTimeoutException("Read timed out"))))
                .isInstanceOf(IaTimeoutException.class);
        assertThat(IaGateway.sansReponse(new RuntimeException("x", new java.net.ConnectException("Connection refused"))))
                .isInstanceOf(IaInjoignableException.class);
        assertThat(IaGateway.sansReponse(new RuntimeException("x", new java.net.SocketTimeoutException("connect timed out"))))
                .isInstanceOf(IaInjoignableException.class);
        assertThat(IaGateway.sansReponse(new RuntimeException("Load balancer does not contain an instance"))).isInstanceOf(IaInjoignableException.class);
    }

    @Test
    void codesDistinctsDansApiErrorAvecUnStatut503() {
        var requete = new MockHttpServletRequest("POST", "/api/cours/1/fiche/generer");
        var delai = new IaExceptionHandler().delai(new IaTimeoutException(), requete);
        var injoignable = new IaExceptionHandler().injoignable(new IaInjoignableException(), requete);

        assertThat(delai.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(injoignable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(delai.getBody().error()).isEqualTo("IA_TIMEOUT");
        assertThat(injoignable.getBody().error()).isEqualTo("IA_INJOIGNABLE");
        assertThat(delai.getBody().message()).isEqualTo("L'IA met trop de temps à répondre, réessaie dans un instant");
        assertThat(injoignable.getBody().message()).isEqualTo("Le service d'IA est injoignable");
        assertThat(delai.getBody().status()).isEqualTo(503).isEqualTo(injoignable.getBody().status());
        assertThat(delai.getBody().path()).isEqualTo("/api/cours/1/fiche/generer");
    }

    // ------------------------------------------------------------------ faux serveur

    private static HttpServer demarrerServeur() {
        try {
            HttpServer serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            serveur.createContext("/", IaDelaiTest::repondre);
            serveur.start();
            return serveur;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void repondre(HttpExchange echange) throws IOException {
        echange.getRequestBody().readAllBytes();
        String comportement = COMPORTEMENT.get();
        try {
            if ("lent".equals(comportement)) {
                Thread.sleep(3_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int statut = switch (comportement) {
            case "504", "504-vide" -> 504;
            case "503" -> 503;
            default -> 200;
        };
        byte[] corps = switch (comportement) {
            case "504" -> "{\"success\":false,\"status\":504,\"error\":\"LLM_TIMEOUT\",\"message\":\"Le fournisseur LLM n'a pas répondu à temps\"}"
                    .getBytes(StandardCharsets.UTF_8);
            case "503" -> "{\"success\":false,\"status\":503,\"error\":\"X\"}".getBytes(StandardCharsets.UTF_8);
            case "504-vide" -> new byte[0];
            default -> "{\"success\":true,\"data\":{\"resume\":\"r\",\"motsCles\":[],\"notions\":[],\"niveau\":\"DEBUTANT\",\"scoreFidelite\":1}}"
                    .getBytes(StandardCharsets.UTF_8);
        };
        echange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            echange.sendResponseHeaders(statut, corps.length == 0 ? -1 : corps.length);
            if (corps.length > 0) {
                echange.getResponseBody().write(corps);
            }
        } catch (IOException e) {
            // le client a abandonné (délai) : rien à écrire
        } finally {
            echange.close();
        }
    }
}

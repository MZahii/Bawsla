package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tn.esprit.bawsla.cours.client.AudioIaRequestDto;
import tn.esprit.bawsla.cours.client.FicheIaDto;
import tn.esprit.bawsla.cours.client.FicheIaRequestDto;
import tn.esprit.bawsla.cours.client.FideliteIaDto;
import tn.esprit.bawsla.cours.client.FideliteIaRequestDto;
import tn.esprit.bawsla.cours.client.NotionIaDto;
import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.common.exception.UnauthorizedException;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.service.IaGateway;

/**
 * Le VRAI client Feign de l'IA contre un faux serveur HTTP local (qui joue la gateway) : décodage de l'enveloppe
 * ApiResponse, octets du MP3, transmission du Authorization et traduction des erreurs en exceptions du module.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cours_ia_client;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class IaClientTest {

    private static final HttpServer SERVEUR = demarrerServeur();
    private static final byte[] MP3 = {'I', 'D', '3', 1, 2, 3, (byte) 0xFF, (byte) 0xFB};

    /** chemin -> [statut, type, corps] programmés par chaque test. */
    private static final Map<String, Object[]> REPONSES = new ConcurrentHashMap<>();
    private static final List<String> AUTORISATIONS = new CopyOnWriteArrayList<>();
    private static final List<String> CORPS_RECUS = new CopyOnWriteArrayList<>();

    @Autowired IaGateway ia;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("cours.ia.gateway-url", () -> "http://localhost:" + SERVEUR.getAddress().getPort());
        registry.add("cours.stockage.dossier", () -> System.getProperty("java.io.tmpdir") + "/bawsla-ia-client-test");
    }

    @BeforeEach
    void reinitialiser() {
        REPONSES.clear();
        AUTORISATIONS.clear();
        CORPS_RECUS.clear();
    }

    @AfterAll
    static void arreter() {
        SERVEUR.stop(0);
    }

    @Test
    void ficheDecodeeEtAuthorizationTransmis() {
        REPONSES.put("/api/ai/cours/fiche", new Object[] {200, "application/json", """
                {"success":true,"data":{"resume":"Résumé","motsCles":["SQL"],"notions":[{"titre":"JOIN","definition":"d","extraitSource":"e","justifiee":false}],
                 "niveau":"DEBUTANT","scoreFidelite":64,"alertes":["a1"],"versionPrompt":"v3","modele":"gemini-x","champInconnu":1},
                 "timestamp":"2026-10-11T16:48:07.164710Z"}"""});

        FicheIaDto fiche = ia.fiche("Bearer jeton-xyz", new FicheIaRequestDto(5L, "Texte du cours", Niveau.DEBUTANT));

        assertThat(fiche.resume()).isEqualTo("Résumé");
        assertThat(fiche.scoreFidelite()).isEqualTo(64);
        assertThat(fiche.niveau()).isEqualTo(Niveau.DEBUTANT);
        assertThat(fiche.notions()).containsExactly(new NotionIaDto("JOIN", "d", "e", false));
        assertThat(AUTORISATIONS).containsExactly("Bearer jeton-xyz");
        assertThat(CORPS_RECUS.get(0)).contains("\"coursId\":5").contains("\"texte\":\"Texte du cours\"").contains("\"niveau\":\"DEBUTANT\"");
    }

    @Test
    void fideliteDecodee() {
        REPONSES.put("/api/ai/cours/fidelite", new Object[] {200, "application/json",
                "{\"success\":true,\"data\":{\"scoreFidelite\":88,\"alertes\":[],\"notions\":[{\"titre\":\"JOIN\",\"justifiee\":true}]}}"});
        FideliteIaDto f = ia.fidelite("Bearer t", new FideliteIaRequestDto(
                new FideliteIaRequestDto.Fiche("r", List.of("m"), List.of(new NotionIaDto("JOIN", "d", "e"))), "texte"));
        assertThat(f.scoreFidelite()).isEqualTo(88);
        assertThat(f.notions()).containsExactly(new FideliteIaDto.NotionJustifieeDto("JOIN", true));
        assertThat(CORPS_RECUS.get(0)).contains("\"fiche\":{").contains("\"motsCles\":[\"m\"]");
    }

    @Test
    void audioRecuEnOctets() {
        REPONSES.put("/api/ai/cours/audio", new Object[] {200, "audio/mpeg", MP3});
        assertThat(ia.audio("Bearer t", new AudioIaRequestDto("Bonjour"))).isEqualTo(MP3);
    }

    @Test
    void erreur400DeLIADevientUneBadRequest() {
        REPONSES.put("/api/ai/cours/fiche", new Object[] {400, "application/json",
                "{\"success\":false,\"status\":400,\"error\":\"BAD_REQUEST\",\"message\":\"Données invalides\"}"});
        assertThatThrownBy(() -> ia.fiche("Bearer t", new FicheIaRequestDto(1L, "x", Niveau.AVANCE)))
                .isInstanceOf(BadRequestException.class).hasMessage("Données invalides");
    }

    @Test
    void quotaEtPannesDeLIADeviennentDesServiceUnavailable() {
        REPONSES.put("/api/ai/cours/fiche", new Object[] {429, "application/json",
                "{\"success\":false,\"status\":429,\"error\":\"LLM_QUOTA_EXCEEDED\",\"message\":\"Quota du fournisseur LLM atteint, réessaie plus tard\"}"});
        assertThatThrownBy(() -> ia.fiche("Bearer t", new FicheIaRequestDto(1L, "x", Niveau.AVANCE)))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("Quota");

        REPONSES.put("/api/ai/cours/fiche", new Object[] {504, "application/json",
                "{\"success\":false,\"status\":504,\"error\":\"LLM_TIMEOUT\",\"message\":\"Le fournisseur LLM n'a pas répondu à temps\"}"});
        assertThatThrownBy(() -> ia.fiche("Bearer t", new FicheIaRequestDto(1L, "x", Niveau.AVANCE)))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("pas répondu");

        REPONSES.put("/api/ai/cours/audio", new Object[] {503, "text/html", "<html>Bad gateway</html>"});
        assertThatThrownBy(() -> ia.audio("Bearer t", new AudioIaRequestDto("x")))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("indisponible");
    }

    @Test
    void sessionRefuseeParLaGateway() {
        REPONSES.put("/api/ai/cours/fiche", new Object[] {401, "application/json",
                "{\"success\":false,\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Token expiré\"}"});
        assertThatThrownBy(() -> ia.fiche("Bearer vieux", new FicheIaRequestDto(1L, "x", Niveau.AVANCE)))
                .isInstanceOf(ServiceUnavailableException.class).hasMessageContaining("Reconnecte-toi");
    }

    @Test
    void reponseVideOuIllisible() {
        REPONSES.put("/api/ai/cours/fiche", new Object[] {200, "application/json", "{\"success\":true}"});
        assertThatThrownBy(() -> ia.fiche("Bearer t", new FicheIaRequestDto(1L, "x", Niveau.AVANCE)))
                .isInstanceOf(ServiceUnavailableException.class);
        REPONSES.put("/api/ai/cours/audio", new Object[] {200, "audio/mpeg", new byte[0]});
        assertThatThrownBy(() -> ia.audio("Bearer t", new AudioIaRequestDto("x"))).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void sansAuthorizationOnNAppellePasLIA() {
        assertThatThrownBy(() -> ia.fiche(" ", new FicheIaRequestDto(1L, "x", Niveau.AVANCE))).isInstanceOf(UnauthorizedException.class);
        assertThat(AUTORISATIONS).isEmpty();
    }

    // ------------------------------------------------------------------ faux serveur

    private static HttpServer demarrerServeur() {
        try {
            HttpServer serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            serveur.createContext("/", IaClientTest::repondre);
            serveur.start();
            return serveur;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void repondre(HttpExchange echange) throws IOException {
        String autorisation = echange.getRequestHeaders().getFirst("Authorization");
        if (autorisation != null) {
            AUTORISATIONS.add(autorisation);
        }
        CORPS_RECUS.add(new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Object[] reponse = REPONSES.get(echange.getRequestURI().getPath());
        int statut = reponse == null ? 404 : (int) reponse[0];
        String type = reponse == null ? "application/json" : (String) reponse[1];
        byte[] corps = reponse == null ? new byte[0]
                : reponse[2] instanceof byte[] octets ? octets : ((String) reponse[2]).getBytes(StandardCharsets.UTF_8);
        echange.getResponseHeaders().set("Content-Type", type);
        echange.sendResponseHeaders(statut, corps.length == 0 ? -1 : corps.length);
        if (corps.length > 0) {
            echange.getResponseBody().write(corps);
        }
        echange.close();
    }
}

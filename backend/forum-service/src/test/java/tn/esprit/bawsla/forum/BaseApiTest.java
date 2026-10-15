package tn.esprit.bawsla.forum;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import feign.FeignException;
import feign.Request;
import tn.esprit.bawsla.forum.client.CoursClient;
import tn.esprit.bawsla.forum.client.CoursDto;
import tn.esprit.bawsla.forum.client.QuizClient;
import tn.esprit.bawsla.forum.client.UserClient;
import tn.esprit.bawsla.forum.client.UserDto;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.service.AnnuaireService;

/**
 * Socle des tests d'API : base H2 vidée avant chaque test, services distants simulés.
 * Cours 5 « Bases de données » (formatrice 10) ; cours 404 inexistant ; cours 77 injoignable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class BaseApiTest {

    static final long ADMIN = 1L;
    static final long FORMATRICE = 10L;
    static final long AUTRE_FORMATEUR = 20L;
    static final long YASSINE = 30L;
    static final long SARA = 31L;
    static final long COURS = 5L;
    static final long COURS_ABSENT = 404L;
    static final long COURS_INJOIGNABLE = 77L;

    static final String ADMIN_ROLE = "ADMIN";
    static final String ENSEIGNANT = "ENSEIGNANT";
    static final String ETUDIANT = "ETUDIANT";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AnnuaireService annuaire;

    @MockitoBean
    CoursClient coursClient;

    @MockitoBean
    UserClient userClient;

    @MockitoBean
    QuizClient quizClient;

    @BeforeEach
    void preparer() {
        viderBase();
        annuaire.oublierCaches();
        when(coursClient.getCours(COURS)).thenReturn(
                ApiResponse.ok(new CoursDto(COURS, "Bases de données", "Informatique", "DEBUTANT", FORMATRICE)));
        Request requete = Request.create(Request.HttpMethod.GET, "/api/cours/404", Map.of(), null,
                StandardCharsets.UTF_8, null);
        when(coursClient.getCours(COURS_ABSENT)).thenThrow(
                new FeignException.NotFound("absent", requete, null, Map.of()));
        when(coursClient.getCours(COURS_INJOIGNABLE)).thenThrow(new IllegalStateException("cours-service arrêté"));
        Map<Long, UserDto> membres = Map.of(
                ADMIN, new UserDto(ADMIN, "Admin", "Bawsla", ADMIN_ROLE),
                FORMATRICE, new UserDto(FORMATRICE, "Trabelsi", "Imen", ENSEIGNANT),
                AUTRE_FORMATEUR, new UserDto(AUTRE_FORMATEUR, "Ben Ali", "Sami", ENSEIGNANT),
                YASSINE, new UserDto(YASSINE, "Gharbi", "Yassine", ETUDIANT),
                SARA, new UserDto(SARA, "Mansouri", "Sara", ETUDIANT));
        when(userClient.getUser(anyLong())).thenAnswer(inv -> ApiResponse.ok(membres.get(inv.<Long>getArgument(0))));
    }

    private void viderBase() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'PUBLIC'", String.class);
        tables.forEach(t -> jdbc.execute("TRUNCATE TABLE " + t + " RESTART IDENTITY"));
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // ------------------------------------------------------------------ Requêtes

    ResultActions en(long userId, String role, MockHttpServletRequestBuilder requete) throws Exception {
        return mvc.perform(requete.header("X-User-Id", userId).header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON));
    }

    ResultActions en(long userId, String role, MockHttpServletRequestBuilder requete, Object corps) throws Exception {
        return mvc.perform(requete.header("X-User-Id", userId).header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corps)));
    }

    static String role(long userId) {
        if (userId == ADMIN) {
            return ADMIN_ROLE;
        }
        return userId == FORMATRICE || userId == AUTRE_FORMATEUR ? ENSEIGNANT : ETUDIANT;
    }

    static Map<String, Object> question(long coursId, String titre) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("coursId", coursId);
        corps.put("type", "QUESTION");
        corps.put("titre", titre);
        corps.put("contenu", "Mon `LEFT JOIN` renvoie autant de lignes qu'un `INNER JOIN`. Pourquoi ?");
        corps.put("etiquettes", List.of("SQL", "Jointures"));
        return corps;
    }

    long publier(long auteur, Map<String, Object> corps) throws Exception {
        MvcResult r = en(auteur, role(auteur), post("/api/forum/discussions"), corps)
                .andExpect(status().isCreated()).andReturn();
        return id(r);
    }

    long publierQuestion(long auteur, String titre) throws Exception {
        return publier(auteur, question(COURS, titre));
    }

    long repondre(long auteur, long discussionId, String contenu) throws Exception {
        MvcResult r = en(auteur, role(auteur), post("/api/forum/discussions/{id}/reponses", discussionId),
                Map.of("contenu", contenu, "anonyme", false))
                .andExpect(status().isCreated()).andReturn();
        return id(r);
    }

    ResultActions ouvrir(long userId, long discussionId) throws Exception {
        return en(userId, role(userId), get("/api/forum/discussions/{id}", discussionId));
    }

    static long id(MvcResult r) throws Exception {
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data.id")).longValue();
    }
}

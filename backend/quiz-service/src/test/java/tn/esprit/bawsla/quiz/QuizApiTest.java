package tn.esprit.bawsla.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import tn.esprit.bawsla.quiz.client.CoursClient;
import tn.esprit.bawsla.quiz.client.CoursDto;
import tn.esprit.bawsla.quiz.common.ApiResponse;

/**
 * API du module Quiz : création et règles d'un quiz, brouillon / publication, passage sans fuite des bonnes réponses,
 * correction serveur, notions ratées, remédiation IA (remplacée par {@link FauxIa}), examen final et certificat, droits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(QuizApiTest.IaDeTest.class)
class QuizApiTest {

    private static final long ADMIN = 1L;
    private static final long FORMATEUR = 10L;
    private static final long AUTRE_FORMATEUR = 20L;
    private static final long COURS = 5L;
    private static final String JETON = "Bearer jeton-de-test";

    @TestConfiguration
    static class IaDeTest {
        @Bean
        @Primary
        FauxIa fauxIa() {
            return new FauxIa();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FauxIa ia;

    /** cours-service simulé : getCours renvoie null, le cours est donc considéré comme existant. */
    @MockitoBean
    private CoursClient coursClient;

    // ---------- Création et règles ----------

    @Test
    void unFormateurCreeUnQuizEnBrouillonAvecSesQuestions() throws Exception {
        mvc.perform(en(post("/api/quiz"), FORMATEUR, "ENSEIGNANT").content(quizJson("QUIZ", 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.data.nbQuestions").value(3))
                .andExpect(jsonPath("$.data.seuilReussite").value(70))
                .andExpect(jsonPath("$.data.tentativesMax").value(0))
                .andExpect(jsonPath("$.data.enseignantId").value(FORMATEUR));
    }

    @Test
    void unApprenantNePeutPasCreerDeQuiz() throws Exception {
        mvc.perform(en(post("/api/quiz"), 300L, "ETUDIANT").content(quizJson("QUIZ", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uneQuestionDoitAvoirExactementUneBonneReponse() throws Exception {
        String deuxBonnes = """
                {"titre":"Q","coursId":5,"questions":[{"enonce":"?","niveauBloom":"COMPRENDRE","competence":"SQL",
                 "choix":[{"texte":"a","estCorrecte":true},{"texte":"b","estCorrecte":true}]}]}""";
        mvc.perform(en(post("/api/quiz"), FORMATEUR, "ENSEIGNANT").content(deuxBonnes))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unFormateurNeCreePasDeQuizSurLeCoursDUnAutre() throws Exception {
        when(coursClient.getCours(anyLong()))
                .thenReturn(ApiResponse.ok(new CoursDto(COURS, "SQL", "INFORMATIQUE", "DEBUTANT", AUTRE_FORMATEUR)));
        mvc.perform(en(post("/api/quiz"), FORMATEUR, "ENSEIGNANT").content(quizJson("EXAMEN", 1)))
                .andExpect(status().isForbidden());
        mvc.perform(en(post("/api/quiz"), ADMIN, "ADMIN").content(quizJson("EXAMEN", 1)))
                .andExpect(status().isCreated());
    }

    @Test
    void unQuizSansQuestionNeSePubliePas() throws Exception {
        long id = creer("QUIZ", 0);
        mvc.perform(en(post("/api/quiz/" + id + "/publier"), FORMATEUR, "ENSEIGNANT"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Visibilité ----------

    @Test
    void unBrouillonEstInvisiblePourLesApprenantsJusquaSaPublication() throws Exception {
        long id = creer("QUIZ", 2);
        mvc.perform(en(get("/api/quiz/" + id + "/passage"), 301L, "ETUDIANT")).andExpect(status().isNotFound());
        mvc.perform(en(get("/api/quiz?coursId=" + COURS), 301L, "ETUDIANT"))
                .andExpect(content().string(not(containsString("\"id\":" + id + ","))));

        publier(id);
        mvc.perform(en(get("/api/quiz?coursId=" + COURS), 301L, "ETUDIANT"))
                .andExpect(content().string(containsString("\"id\":" + id + ",")));
    }

    @Test
    void lePassageNeDevoilePasLesBonnesReponses() throws Exception {
        long id = creer("QUIZ", 2);
        publier(id);
        mvc.perform(en(get("/api/quiz/" + id + "/passage"), 302L, "ETUDIANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions", hasSize(2)))
                .andExpect(content().string(not(containsString("estCorrecte"))))
                .andExpect(content().string(not(containsString("erreurAssociee"))));
    }

    @Test
    void seulLAuteurVoitLeDetailEtModifieLeQuiz() throws Exception {
        long id = creer("QUIZ", 1);
        mvc.perform(en(get("/api/quiz/" + id + "/detail"), AUTRE_FORMATEUR, "ENSEIGNANT")).andExpect(status().isForbidden());
        mvc.perform(en(put("/api/quiz/" + id), AUTRE_FORMATEUR, "ENSEIGNANT").content(quizJson("QUIZ", 1)))
                .andExpect(status().isForbidden());
        mvc.perform(en(get("/api/quiz/" + id + "/detail"), ADMIN, "ADMIN")).andExpect(status().isOk());
        mvc.perform(en(put("/api/quiz/" + id), FORMATEUR, "ENSEIGNANT").content(quizJson("QUIZ", 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nbQuestions").value(4));
    }

    // ---------- Passage, correction, notions ratées ----------

    @Test
    void laTentativeEstCorrigeeCoteServeurEtLesErreursDeviennentDesNotionsRatees() throws Exception {
        long apprenant = 303L;
        long id = creer("QUIZ", 4);
        publier(id);
        Passage p = passage(id);

        // 2 bonnes réponses sur 4 : 50 %, sous le seuil de 70 %
        String reponses = reponsesJson(p, Arrays.asList(p.bonnes.get(0), p.bonnes.get(1), p.mauvaises.get(2), null));
        mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), apprenant, "ETUDIANT").content(reponses))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nbCorrectes").value(2))
                .andExpect(jsonPath("$.data.pourcentage").value(50))
                .andExpect(jsonPath("$.data.reussie").value(false))
                .andExpect(jsonPath("$.data.corrections", hasSize(4)))
                .andExpect(jsonPath("$.data.corrections[2].diagnostic").value("Erreur 3"))
                .andExpect(jsonPath("$.data.corrections[3].diagnostic").value("Question laissée sans réponse."));

        mvc.perform(en(get("/api/quiz/notions-ratees?etudiantId=" + apprenant), apprenant, "ETUDIANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].nbErreurs").value(1))
                .andExpect(jsonPath("$.data[0].tauxReussite").value(0.0))
                .andExpect(jsonPath("$.data[0].coursId").value(COURS));

        mvc.perform(en(get("/api/quiz/tentatives/mes"), apprenant, "ETUDIANT"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].pourcentage").value(50));
    }

    @Test
    void contrat6_lesTentativesDUnApprenantAvecSesBonnesReponses() throws Exception {
        long apprenant = 310L;
        long id = creer("QUIZ", 4);
        publier(id);
        Passage p = passage(id);
        mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), apprenant, "ETUDIANT")
                        .content(reponsesJson(p, Arrays.asList(p.bonnes.get(0), p.bonnes.get(1), p.bonnes.get(2), p.mauvaises.get(3)))))
                .andExpect(status().isCreated());

        mvc.perform(en(get("/api/quiz/tentatives?etudiantId=" + apprenant), FORMATEUR, "ENSEIGNANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].quizId").value(id))
                .andExpect(jsonPath("$.data[0].coursId").value(COURS))
                .andExpect(jsonPath("$.data[0].nbCorrectes").value(3))
                .andExpect(jsonPath("$.data[0].nbQuestions").value(4))
                .andExpect(jsonPath("$.data[0].pourcentage").value(75))
                .andExpect(jsonPath("$.data[0].reussie").value(true))
                .andExpect(jsonPath("$.data[0].dateTentative").exists());
        mvc.perform(en(get("/api/quiz/tentatives?etudiantId=" + apprenant), apprenant, "ETUDIANT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(en(get("/api/quiz/tentatives?etudiantId=" + apprenant), ADMIN, "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void contrat6_reserveALApprenantLuiMemeAuxFormateursEtAdmins() throws Exception {
        mvc.perform(en(get("/api/quiz/tentatives?etudiantId=311"), 312L, "ETUDIANT")).andExpect(status().isForbidden());
        mvc.perform(en(get("/api/quiz/tentatives"), FORMATEUR, "ENSEIGNANT")).andExpect(status().isBadRequest());
        mvc.perform(en(get("/api/quiz/tentatives?etudiantId=311"), FORMATEUR, "ENSEIGNANT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void lesNotionsRateesDUnAutreApprenantSontReserveesAuxFormateursEtAdmins() throws Exception {
        mvc.perform(en(get("/api/quiz/notions-ratees?etudiantId=304"), 305L, "ETUDIANT")).andExpect(status().isForbidden());
        mvc.perform(en(get("/api/quiz/notions-ratees?etudiantId=304"), FORMATEUR, "ENSEIGNANT")).andExpect(status().isOk());
        mvc.perform(en(get("/api/quiz/notions-ratees?etudiantId=304"), ADMIN, "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void uneReponseQuiNAppartientPasAuQuizEstRefusee() throws Exception {
        long id = creer("QUIZ", 1);
        publier(id);
        mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), 306L, "ETUDIANT")
                        .content("{\"reponses\":[{\"questionId\":999999,\"optionId\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unBrouillonNeSePassePas() throws Exception {
        long id = creer("QUIZ", 1);
        mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), 307L, "ETUDIANT").content("{\"reponses\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Remédiation IA ----------

    @Test
    void laRemediationAppelleLIaUneSeuleFoisSansDonneePersonnelleEtCorrigeLeRattrapage() throws Exception {
        long apprenant = 308L;
        long id = creer("QUIZ", 1);
        publier(id);
        Passage p = passage(id);
        String resultat = mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), apprenant, "ETUDIANT")
                        .content(reponsesJson(p, List.of(p.mauvaises.get(0)))))
                .andReturn().getResponse().getContentAsString();
        Number reponseId = JsonPath.read(resultat, "$.data.corrections[0].reponseId");
        int avant = ia.appels.get();

        // un autre apprenant ne peut pas demander l'explication de cette erreur
        mvc.perform(en(post("/api/quiz/reponses/" + reponseId + "/remediation"), 309L, "ETUDIANT"))
                .andExpect(status().isForbidden());

        String remediation = mvc.perform(en(post("/api/quiz/reponses/" + reponseId + "/remediation"), apprenant, "ETUDIANT")
                        .header("Authorization", JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.explication").value("Explication de test"))
                .andExpect(jsonPath("$.data.questionRattrapage.choix", hasSize(3)))
                .andExpect(content().string(not(containsString("estCorrecte"))))
                .andExpect(content().string(not(containsString("raisonnement"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(ia.appels.get()).isEqualTo(avant + 1);
        assertThat(ia.jetons.get(ia.jetons.size() - 1)).isEqualTo(JETON);
        assertThat(ia.corps.get(ia.corps.size() - 1).toString()).doesNotContain(String.valueOf(apprenant));

        // deuxième demande : relue en base, pas de nouvel appel IA
        mvc.perform(en(post("/api/quiz/reponses/" + reponseId + "/remediation"), apprenant, "ETUDIANT"))
                .andExpect(status().isOk());
        assertThat(ia.appels.get()).isEqualTo(avant + 1);

        Number remediationId = JsonPath.read(remediation, "$.data.notionRateeId");
        mvc.perform(en(post("/api/quiz/remediations/" + remediationId + "/rattrapage"), apprenant, "ETUDIANT")
                        .content("{\"indexChoix\":1}"))
                .andExpect(jsonPath("$.data.correcte").value(false))
                .andExpect(jsonPath("$.data.indexBonne").value(0));
        mvc.perform(en(post("/api/quiz/remediations/" + remediationId + "/rattrapage"), apprenant, "ETUDIANT")
                        .content("{\"indexChoix\":0}"))
                .andExpect(jsonPath("$.data.correcte").value(true));
        mvc.perform(en(post("/api/quiz/remediations/" + remediationId + "/rattrapage"), apprenant, "ETUDIANT")
                        .content("{\"indexChoix\":7}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Examen final et certificat ----------

    @Test
    void examenReussiDelivreUnCertificatVerifiableUneSeuleFois() throws Exception {
        long apprenant = 310L;
        long id = creer("EXAMEN", 2);
        // le titre du cours est copié dans le certificat à l'émission (Feign vers cours-service)
        when(coursClient.getCours(anyLong())).thenReturn(ApiResponse.ok(new CoursDto(1L, "Algorithmique", null, null, 7L)));
        publier(id);
        Passage p = passage(id);

        String resultat = mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), apprenant, "ETUDIANT")
                        .content(reponsesJson(p, p.bonnes)))
                .andExpect(jsonPath("$.data.reussie").value(true))
                .andReturn().getResponse().getContentAsString();
        String numero = JsonPath.read(resultat, "$.data.certificatNumero");
        assertThat(numero).matches("BW-\\d{4}-[0-9A-F]{8}");

        mvc.perform(en(get("/api/quiz/certificats/mes"), apprenant, "ETUDIANT"))
                .andExpect(jsonPath("$.data[0].numero").value(numero));
        // vérification publique : sans compte (aucun X-User-*) et sans l'identifiant de l'apprenant
        mvc.perform(get("/api/quiz/certificats/verification/" + numero.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.titreCours").value("Algorithmique"))
                .andExpect(jsonPath("$.data", not(hasKey("etudiantId"))));
        mvc.perform(get("/api/quiz/certificats/verification/BW-0000-INCONNU")).andExpect(status().isNotFound());

        // un seul certificat par cours
        mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), apprenant, "ETUDIANT").content(reponsesJson(p, p.bonnes)))
                .andExpect(status().isConflict());
        // liste complète réservée à l'admin
        mvc.perform(en(get("/api/quiz/certificats"), apprenant, "ETUDIANT")).andExpect(status().isForbidden());
        mvc.perform(en(get("/api/quiz/certificats"), ADMIN, "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void lExamenEstLimiteATroisTentatives() throws Exception {
        long apprenant = 311L;
        long id = creer("EXAMEN", 1);
        publier(id);
        Passage p = passage(id);
        for (int i = 0; i < 3; i++) {
            mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), apprenant, "ETUDIANT")
                            .content(reponsesJson(p, p.mauvaises)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), apprenant, "ETUDIANT").content(reponsesJson(p, p.bonnes)))
                .andExpect(status().isConflict());
    }

    // ---------- Studio du formateur ----------

    @Test
    void leStudioEtLesStatistiquesRefletentLesTentatives() throws Exception {
        long id = creer("QUIZ", 2);
        publier(id);
        Passage p = passage(id);
        mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), 312L, "ETUDIANT").content(reponsesJson(p, p.bonnes)));
        mvc.perform(en(post("/api/quiz/" + id + "/tentatives"), 313L, "ETUDIANT").content(reponsesJson(p, p.mauvaises)));

        mvc.perform(en(get("/api/quiz/" + id + "/statistiques"), FORMATEUR, "ENSEIGNANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nbTentatives").value(2))
                .andExpect(jsonPath("$.data.moyenne").value(50.0))
                .andExpect(jsonPath("$.data.distribution", hasSize(5)));
        mvc.perform(en(get("/api/quiz/" + id + "/statistiques"), AUTRE_FORMATEUR, "ENSEIGNANT"))
                .andExpect(status().isForbidden());
        mvc.perform(en(get("/api/quiz/mes-quiz"), FORMATEUR, "ENSEIGNANT")).andExpect(status().isOk());

        // un quiz déjà passé ne se supprime pas
        mvc.perform(en(delete("/api/quiz/" + id), FORMATEUR, "ENSEIGNANT")).andExpect(status().isConflict());
    }

    // ---------- Outils ----------

    private record Passage(List<Long> questions, List<Long> bonnes, List<Long> mauvaises) {
    }

    private MockHttpServletRequestBuilder en(MockHttpServletRequestBuilder req, long userId, String role) {
        return req.header("X-User-Id", userId).header("X-User-Role", role).contentType(MediaType.APPLICATION_JSON);
    }

    private long creer(String type, int nbQuestions) throws Exception {
        String json = mvc.perform(en(post("/api/quiz"), FORMATEUR, "ENSEIGNANT").content(quizJson(type, nbQuestions)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private void publier(long id) throws Exception {
        mvc.perform(en(post("/api/quiz/" + id + "/publier"), FORMATEUR, "ENSEIGNANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("PUBLIE"));
    }

    /** Lit les identifiants via le détail du formateur (le passage ne dit pas quelle option est la bonne). */
    private Passage passage(long id) throws Exception {
        String json = mvc.perform(en(get("/api/quiz/" + id + "/detail"), FORMATEUR, "ENSEIGNANT"))
                .andReturn().getResponse().getContentAsString();
        List<Number> questions = JsonPath.read(json, "$.data.questions[*].id");
        List<Long> q = new ArrayList<>(), bonnes = new ArrayList<>(), mauvaises = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            q.add(questions.get(i).longValue());
            List<Number> b = JsonPath.read(json, "$.data.questions[" + i + "].choix[?(@.estCorrecte == true)].id");
            List<Number> m = JsonPath.read(json, "$.data.questions[" + i + "].choix[?(@.estCorrecte == false)].id");
            bonnes.add(b.get(0).longValue());
            mauvaises.add(m.get(0).longValue());
        }
        return new Passage(q, bonnes, mauvaises);
    }

    private static String reponsesJson(Passage p, List<Long> options) {
        List<String> lignes = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            lignes.add("{\"questionId\":" + p.questions.get(i) + ",\"optionId\":" + options.get(i) + "}");
        }
        return "{\"reponses\":[" + String.join(",", lignes) + "]}";
    }

    /** Question i : compétence « Notion i », bonne réponse en 2e position, distracteur avec « Erreur i ». */
    private static String quizJson(String type, int nbQuestions) {
        List<String> questions = new ArrayList<>();
        for (int i = 1; i <= nbQuestions; i++) {
            questions.add("""
                    {"enonce":"Question %d ?","niveauBloom":"COMPRENDRE","competence":"Notion %d","explication":"Parce que.",
                     "choix":[{"texte":"Faux %d","estCorrecte":false,"erreurAssociee":"Erreur %d"},
                              {"texte":"Vrai %d","estCorrecte":true}]}""".formatted(i, i, i, i, i));
        }
        return """
                {"titre":"Jointures SQL","coursId":%d,"type":"%s","theme":"Jointures","questions":[%s]}"""
                .formatted(COURS, type, String.join(",", questions));
    }
}

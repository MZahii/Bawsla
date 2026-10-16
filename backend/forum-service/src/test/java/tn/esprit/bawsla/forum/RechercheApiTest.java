package tn.esprit.bawsla.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import tn.esprit.bawsla.forum.client.ChapitreDto;
import tn.esprit.bawsla.forum.client.CoursTexteDto;
import tn.esprit.bawsla.forum.client.IndexerIaDto;
import tn.esprit.bawsla.forum.client.NotionRateeDto;
import tn.esprit.bawsla.forum.client.VectoriserIaDto;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.IndexCours;
import tn.esprit.bawsla.forum.entity.enums.StatutIndex;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.IndexCoursRepository;
import tn.esprit.bawsla.forum.repository.PassageCoursRepository;

/**
 * Lot 4 : vectorisation des discussions, indexation des cours, questions similaires en direct (reformulation
 * derja / arabizi), doublons, recherche hybride RRF, discussions liées, « Pour toi », repli en mots-clés.
 */
class RechercheApiTest extends BaseApiTest {

    private static final String RESOLUE = "Pourquoi mon LEFT JOIN renvoie autant de lignes qu'un INNER JOIN ?";
    private static final String DERJA = "kifech nfiltri b LEFT JOIN bla ma nkhasser les lignes ?";

    @Autowired
    private DiscussionRepository discussions;

    @Autowired
    private IndexCoursRepository index;

    @Autowired
    private PassageCoursRepository passages;

    // ---------------------------------------------------------------- Vectorisation

    @Test
    void uneDiscussionPublieeEstVectoriseeAvecLeJetonDeLUtilisateur() throws Exception {
        String reponse = mvc.perform(post("/api/forum/discussions").header("Authorization", "Bearer jeton-yassine")
                        .header("X-User-Id", YASSINE).header("X-User-Role", ETUDIANT)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(question(COURS, RESOLUE))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(reponse, "$.data.id")).longValue();

        Discussion d = discussions.findById(id).orElseThrow();
        assertThat(d.getEmbedding()).hasSize(FauxIa.DIMENSION);
        assertThat(d.getModeleEmbedding()).isEqualTo(FauxIa.MODELE);
        assertThat(ia.jetons).contains("Bearer jeton-yassine");
        // Rien d'autre que le titre et le début du contenu ne part vers l'IA (ni nom, ni identifiant).
        VectoriserIaDto.Requete envoye = ia.corps.stream().filter(VectoriserIaDto.Requete.class::isInstance)
                .map(VectoriserIaDto.Requete.class::cast).filter(r -> r.tache().equals("DOCUMENT")).findFirst()
                .orElseThrow();
        assertThat(envoye.textes().get(0)).startsWith(RESOLUE).doesNotContain("Yassine").doesNotContain("Gharbi");
    }

    @Test
    void siLIaEstEnPanneLaDiscussionEstPublieeQuandMemeSansVecteur() throws Exception {
        ia.panne = true;
        long id = publierQuestion(YASSINE, RESOLUE);
        assertThat(discussions.findById(id).orElseThrow().getEmbedding()).isNull();
    }

    @Test
    void modifierUneDiscussionLaRevectorise() throws Exception {
        long id = publierQuestion(YASSINE, RESOLUE);
        float[] avant = discussions.findById(id).orElseThrow().getEmbedding();
        en(YASSINE, ETUDIANT, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/forum/discussions/{id}", id), Map.of("titre", "Comment créer un index composite en SQL ?",
                "contenu", "Sur deux colonnes.")).andExpect(status().isOk());
        assertThat(discussions.findById(id).orElseThrow().getEmbedding()).isNotEqualTo(avant);
    }

    // ---------------------------------------------------------------- Questions similaires

    @Test
    void uneQuestionEnDerjaRetrouveLaQuestionResolueFormuleeAutrement() throws Exception {
        long resolue = questionResolue();
        publierSur(SARA, "Comment créer un index composite sur deux colonnes ?",
                "CREATE INDEX sur client_id puis date_commande : dans quel ordre ?");
        ia.reformulations.put(DERJA, RESOLUE);

        en(YASSINE, ETUDIANT, post("/api/forum/discussions/similaires"), Map.of("coursId", COURS, "texte", DERJA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.semantique").value(true))
                .andExpect(jsonPath("$.data.similaires", hasSize(1)))
                .andExpect(jsonPath("$.data.similaires[0].discussionId").value(resolue))
                .andExpect(jsonPath("$.data.similaires[0].statut").value("RESOLUE"))
                .andExpect(jsonPath("$.data.similaires[0].proximite").value("TRES_PROCHE"))
                .andExpect(jsonPath("$.data.doublonId").value(resolue));
    }

    @Test
    void lesSimilairesSontLimitesAuCoursEtExcluentLaDiscussionEnCours() throws Exception {
        long id = publierQuestion(YASSINE, RESOLUE);
        publier(SARA, question(COURS_INJOIGNABLE, RESOLUE));
        en(SARA, ETUDIANT, post("/api/forum/discussions/similaires"),
                Map.of("coursId", COURS, "texte", RESOLUE, "exclureId", id))
                .andExpect(jsonPath("$.data.similaires", hasSize(0)));
        en(SARA, ETUDIANT, post("/api/forum/discussions/similaires"), Map.of("coursId", COURS, "texte", "court"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sansIaLesSimilairesPassentEnMotsClesEtLeDisent() throws Exception {
        long id = publierQuestion(YASSINE, RESOLUE);
        ia.panne = true;
        iaService.oublierCaches();
        en(SARA, ETUDIANT, post("/api/forum/discussions/similaires"),
                Map.of("coursId", COURS, "texte", "LEFT JOIN renvoie autant de lignes qu'un INNER JOIN"))
                .andExpect(jsonPath("$.data.semantique").value(false))
                .andExpect(jsonPath("$.data.similaires[0].discussionId").value(id))
                .andExpect(jsonPath("$.data.doublonId").doesNotExist());
    }

    @Test
    void uneQuestionPublieePendantUnePanneResteTrouvableEnMotsCles() throws Exception {
        ia.panne = true;
        long id = publierQuestion(YASSINE, RESOLUE);  // vectorisation impossible (quota, panne) : aucun vecteur
        ia.panne = false;
        iaService.oublierCaches();
        en(SARA, ETUDIANT, post("/api/forum/discussions/similaires"),
                Map.of("coursId", COURS, "texte", "LEFT JOIN renvoie autant de lignes qu'un INNER JOIN"))
                .andExpect(jsonPath("$.data.semantique").value(true))
                .andExpect(jsonPath("$.data.similaires[0].discussionId").value(id))
                .andExpect(jsonPath("$.data.doublonId").doesNotExist());
    }

    @Test
    void publierUnDoublonDUneQuestionResolueDemandeConfirmation() throws Exception {
        long resolue = questionResolue();
        en(SARA, ETUDIANT, post("/api/forum/discussions"), question(COURS, RESOLUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("discussion n° " + resolue)));
        Map<String, Object> confirme = question(COURS, RESOLUE);
        confirme.put("confirmer", true);
        publier(SARA, confirme);
    }

    // ---------------------------------------------------------------- Recherche et discussions liées

    @Test
    void laRechercheHybrideClasseParPertinenceEtLeModeMotsExactsAussi() throws Exception {
        long jointure = publierQuestion(YASSINE, RESOLUE);
        long index = publierSur(SARA, "Comment créer un index composite sur deux colonnes ?",
                "CREATE INDEX sur client_id puis date_commande : dans quel ordre ?");
        publierSur(SARA, "Que garantit une transaction ACID en cas de panne ?",
                "COMMIT et ROLLBACK : que se passe-t-il si le serveur s'arrête au milieu ?");

        en(SARA, ETUDIANT, get("/api/forum/discussions").param("q", "LEFT JOIN lignes"))
                .andExpect(jsonPath("$.data.contenu[0].id").value(jointure))
                .andExpect(jsonPath("$.data.contenu[0].pertinence").isNumber());
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("q", "index composite").param("mode", "lexical"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.contenu[0].id").value(index));
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("q", "cuisine provençale").param("mode", "lexical"))
                .andExpect(jsonPath("$.data.total").value(0));
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("q", "LEFT JOIN").param("filtre", "miennes"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void lesDiscussionsLieesSontLesPlusProchesDuMemeCours() throws Exception {
        long a = publierQuestion(YASSINE, RESOLUE);
        long b = publierQuestion(SARA, "LEFT JOIN qui renvoie autant de lignes qu'un INNER JOIN, pourquoi ?");
        publierSur(SARA, "Que garantit une transaction ACID en cas de panne ?",
                "COMMIT et ROLLBACK : que se passe-t-il si le serveur s'arrête au milieu ?");
        en(SARA, ETUDIANT, get("/api/forum/discussions/{id}/liees", a))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].discussionId").value(b));
    }

    // ---------------------------------------------------------------- Indexation des cours

    @Test
    void laPremiereQuestionDUnCoursIndexeLeTexteDeDemonstrationUneSeuleFois() throws Exception {
        publierQuestion(YASSINE, RESOLUE);
        IndexCours etat = index.findById(COURS).orElseThrow();
        assertThat(etat.getStatut()).isEqualTo(StatutIndex.A_JOUR);
        assertThat(etat.isSourceDemo()).isTrue();
        assertThat(etat.getNbPassages()).isGreaterThan(5).isEqualTo((int) passages.countByCoursId(COURS));
        assertThat(passages.findByCoursIdOrderByOrdre(COURS)).anyMatch(p -> "LEFT JOIN".equals(p.getTitreSection()));

        publierQuestion(SARA, "Comment créer un index composite sur deux colonnes ?");
        assertThat(ia.indexations.get()).isEqualTo(1);

        en(FORMATRICE, ENSEIGNANT, get("/api/forum/index/{c}", COURS))
                .andExpect(jsonPath("$.data.statut").value("A_JOUR"))
                .andExpect(jsonPath("$.data.sourceDemo").value(true));
        en(YASSINE, ETUDIANT, get("/api/forum/index/{c}", COURS)).andExpect(status().isForbidden());
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/index/{c}/reindexer", COURS)).andExpect(status().isOk());
        assertThat(ia.indexations.get()).isEqualTo(2);
    }

    @Test
    void leTexteReelDuCoursEtSesChapitresRemplacentLaDemonstration() throws Exception {
        when(coursClient.getTexte(COURS)).thenReturn(ApiResponse.ok(new CoursTexteDto(COURS,
                "# Jointures\n\nLe LEFT JOIN garde la table de gauche.", false)));
        when(coursClient.getChapitres(COURS)).thenReturn(ApiResponse.ok(List.of(
                new ChapitreDto(3L, "Index", "Un index accélère les recherches.", 1))));
        en(ADMIN, ADMIN_ROLE, post("/api/forum/index/{c}/reindexer", COURS)).andExpect(status().isOk());

        IndexCours etat = index.findById(COURS).orElseThrow();
        assertThat(etat.isSourceDemo()).isFalse();
        assertThat(etat.getNbPassages()).isEqualTo(2);
        IndexerIaDto.Requete envoye = (IndexerIaDto.Requete) ia.corps.stream()
                .filter(IndexerIaDto.Requete.class::isInstance).findFirst().orElseThrow();
        assertThat(envoye.chapitres()).extracting(IndexerIaDto.Chapitre::id).containsExactly(3L);
    }

    @Test
    void unTexteDeCoursEncoreEnStubNEstPasIndexeLaDemonstrationPrendLeRelais() throws Exception {
        when(coursClient.getTexte(COURS)).thenReturn(ApiResponse.ok(new CoursTexteDto(COURS, "[STUB] titre", true)));
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/index/{c}/reindexer", COURS))
                .andExpect(jsonPath("$.data.statut").value("A_JOUR"))
                .andExpect(jsonPath("$.data.sourceDemo").value(true));
        IndexerIaDto.Requete envoye = (IndexerIaDto.Requete) ia.corps.stream()
                .filter(IndexerIaDto.Requete.class::isInstance).findFirst().orElseThrow();
        assertThat(envoye.texte()).doesNotContain("[STUB]").contains("Chapitre 3 : Les jointures");
    }

    @Test
    void uneIaEnPanneLaisseLIndexEnErreurAvecLaRaison() throws Exception {
        ia.panne = true;
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/index/{c}/reindexer", COURS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("ERREUR"))
                .andExpect(jsonPath("$.data.erreur", containsString("panne")));
        assertThat(passages.countByCoursId(COURS)).isZero();
    }

    // ---------------------------------------------------------------- Pour toi

    @Test
    void pourToiProposeLesFilsResolusLiesAuxNotionsRatees() throws Exception {
        long resolue = questionResolue();
        when(quizClient.notionsRatees(SARA)).thenReturn(ApiResponse.ok(List.of(
                new NotionRateeDto("LEFT JOIN et INNER JOIN", COURS, 4, 0.33),
                new NotionRateeDto("Normalisation", COURS, 1, 0.8))));
        en(SARA, ETUDIANT, get("/api/forum/pour-moi"))
                .andExpect(jsonPath("$.data.disponible").value(true))
                .andExpect(jsonPath("$.data.notions", hasSize(1)))
                .andExpect(jsonPath("$.data.notions[0].notion").value("LEFT JOIN et INNER JOIN"))
                .andExpect(jsonPath("$.data.notions[0].discussions[0].id").value(resolue));
    }

    @Test
    void pourToiSeMasqueSiQuizServiceNeRepondPasOuPourUnFormateur() throws Exception {
        when(quizClient.notionsRatees(SARA)).thenThrow(new IllegalStateException("quiz-service arrêté"));
        en(SARA, ETUDIANT, get("/api/forum/pour-moi")).andExpect(jsonPath("$.data.disponible").value(false));
        en(FORMATRICE, ENSEIGNANT, get("/api/forum/pour-moi")).andExpect(jsonPath("$.data.disponible").value(false));
    }

    // ---------------------------------------------------------------- Aides

    private long publierSur(long auteur, String titre, String contenu) throws Exception {
        Map<String, Object> corps = question(COURS, titre);
        corps.put("contenu", contenu);
        return publier(auteur, corps);
    }

    private long questionResolue() throws Exception {
        long id = publierQuestion(YASSINE, RESOLUE);
        long r = repondre(FORMATRICE, id, "Ta condition sur la table de droite est dans le WHERE : mets-la dans le ON.");
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/accepter/{r}", id, r)).andExpect(status().isOk());
        return id;
    }
}

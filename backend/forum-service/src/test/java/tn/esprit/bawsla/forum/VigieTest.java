package tn.esprit.bawsla.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import tn.esprit.bawsla.forum.client.ReformulerIaDto;
import tn.esprit.bawsla.forum.client.RepondreIaDto;
import tn.esprit.bawsla.forum.entity.ParametresForumCours;
import tn.esprit.bawsla.forum.entity.enums.TypeNotification;
import tn.esprit.bawsla.forum.repository.NotificationRepository;
import tn.esprit.bawsla.forum.repository.ParametresForumCoursRepository;
import tn.esprit.bawsla.forum.repository.PassageForumRepository;
import tn.esprit.bawsla.forum.repository.ReponseIaRepository;
import tn.esprit.bawsla.forum.service.ParametresService;
import tn.esprit.bawsla.forum.service.VigieService;

/**
 * Lot 5 : La Vigie (RAG). Abstention sans LLM, sources et confiance, mode examen, niveau 3 à la demande et ses
 * refus, droits de validation du formateur, base de connaissances du forum, assistant privé et « Publier comme
 * question ». Le cours 5 est indexé avec le texte de démonstration (bases de données) à la première question.
 */
class VigieTest extends BaseApiTest {

    /** Mesuré avec le faux vectoriseur : cosinus 0,71 avec le passage LEFT JOIN (seuil 0,55), contre 0,26 hors cours. */
    private static final String DANS_LE_COURS = "Le LEFT JOIN garde-t-il les lignes sans correspondance ?";
    private static final String PRECISION = "Je veux garder toutes les lignes de la table de gauche, même sans correspondance.";
    private static final String HORS_COURS = "Comment déployer mon application sur Kubernetes avec Helm ?";

    @Autowired
    private ReponseIaRepository reponsesIa;

    @Autowired
    private PassageForumRepository passagesForum;

    @Autowired
    private ParametresForumCoursRepository parametresCours;

    @Autowired
    private ParametresService parametres;

    @Autowired
    private NotificationRepository notifications;

    // ---------------------------------------------------------------- Génération dans un fil

    @Test
    void uneQuestionHorsCoursDonneLAbstentionSansAppelerLeLlm() throws Exception {
        long q = publier(YASSINE, questionSur(HORS_COURS));

        vigie(YASSINE, q)
                .andExpect(jsonPath("$.data.principale.statut").value("HORS_COURS"))
                .andExpect(jsonPath("$.data.principale.texte").value(VigieService.ABSTENTION))
                .andExpect(jsonPath("$.data.principale.sources", hasSize(0)))
                .andExpect(jsonPath("$.data.principale.confiance").doesNotExist())
                .andExpect(jsonPath("$.data.generaleDisponible").value(true));
        assertThat(appelsRepondre()).isZero();
        assertThat(typesNotifies(FORMATRICE)).contains(TypeNotification.HORS_COURS);
        assertThat(typesNotifies(YASSINE)).contains(TypeNotification.VIGIE_PRETE);
    }

    @Test
    void uneQuestionDuCoursRecoitUneReponseSourceeAvecConfiance() throws Exception {
        long q = publier(YASSINE, questionDuCours());

        vigie(SARA, q)
                .andExpect(jsonPath("$.data.principale.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.principale.niveau").value("COURS"))
                .andExpect(jsonPath("$.data.principale.texte", containsString("[1]")))
                .andExpect(jsonPath("$.data.principale.sources[0].numero").value(1))
                .andExpect(jsonPath("$.data.principale.sources[0].origine").value("COURS"))
                .andExpect(jsonPath("$.data.principale.sources[0].cours").value("Bases de données"))
                .andExpect(jsonPath("$.data.principale.sources[0].citation").isNotEmpty())
                .andExpect(jsonPath("$.data.principale.sources[0].lien", startsWith("/app/cours/5/lecture")))
                .andExpect(jsonPath("$.data.principale.confiance").value(FauxIa.CONFIANCE))
                .andExpect(jsonPath("$.data.principale.niveauConfiance").value("ELEVEE"))
                .andExpect(jsonPath("$.data.principale.verifieeParFormateur").value(false))
                .andExpect(jsonPath("$.data.generaleDisponible").value(false));
        // La question part nettoyée de toute identité, avec les passages du cours.
        RepondreIaDto.Requete envoye = ia.corps.stream().filter(RepondreIaDto.Requete.class::isInstance)
                .map(RepondreIaDto.Requete.class::cast).findFirst().orElseThrow();
        assertThat(envoye.passages()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(envoye.question()).doesNotContain("Yassine").doesNotContain("Gharbi");
        assertThat(envoye.mode()).isEqualTo("DIRECTE");
    }

    @Test
    void siLeModeleSAbstientMalgreLesPassagesLeFilAfficheLAbstention() throws Exception {
        ia.horsCours = true;
        long q = publier(YASSINE, questionDuCours());
        vigie(YASSINE, q).andExpect(jsonPath("$.data.principale.statut").value("HORS_COURS"))
                .andExpect(jsonPath("$.data.principale.texte").value(VigieService.ABSTENTION));
    }

    @Test
    void uneIaEnPanneLaisseLaQuestionPublieeEtLaReponseEnErreur() throws Exception {
        long q = publier(YASSINE, questionDuCours());
        ia.panne = true;
        // La régénération part après la réponse HTTP ; l'échec est visible à la lecture suivante.
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/vigie/regenerer", q)).andExpect(status().isOk());
        ia.panne = false;
        vigie(YASSINE, q).andExpect(jsonPath("$.data.principale.statut").value("ERREUR"));
        en(SARA, ETUDIANT, get("/api/forum/discussions/{id}", q)).andExpect(status().isOk());
    }

    @Test
    void laVigieNeRepondPasAuxDiscussionsNiSiElleEstDesactivee() throws Exception {
        Map<String, Object> discussion = question(COURS, "Partagez vos ressources sur SQL");
        discussion.put("type", "DISCUSSION");
        long d = publier(YASSINE, discussion);
        vigie(YASSINE, d).andExpect(jsonPath("$.data.principale").doesNotExist());

        reglerCours(p -> p.setVigieActive(false));
        long q = publier(YASSINE, questionDuCours());
        vigie(YASSINE, q).andExpect(jsonPath("$.data.principale").doesNotExist());
    }

    @Test
    void seulLeFormateurDuCoursRegenere() throws Exception {
        long q = publier(YASSINE, questionDuCours());
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/vigie/regenerer", q)).andExpect(status().isForbidden());
        en(AUTRE_FORMATEUR, ENSEIGNANT, post("/api/forum/discussions/{id}/vigie/regenerer", q))
                .andExpect(status().isForbidden());
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/vigie/regenerer", q))
                .andExpect(status().isOk());
        assertThat(appelsRepondre()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- Mode examen

    @Test
    void enModeExamenLApprenantNeVoitQueLesIndices() throws Exception {
        reglerCours(p -> {
            p.setModeExamen(true);
            p.setModeExamenFin(LocalDateTime.now().plusDays(2));
        });
        long q = publier(YASSINE, questionDuCours());

        vigie(YASSINE, q)
                .andExpect(jsonPath("$.data.principale.mode").value("GUIDEE"))
                .andExpect(jsonPath("$.data.principale.texte").doesNotExist())
                .andExpect(jsonPath("$.data.principale.indices", hasSize(2)))
                .andExpect(jsonPath("$.data.principale.avertissement", containsString("Mode examen")));
        vigie(FORMATRICE, q).andExpect(jsonPath("$.data.principale.texte", containsString("[1]")));
        assertThat(ia.corps.stream().filter(RepondreIaDto.Requete.class::isInstance)
                .map(RepondreIaDto.Requete.class::cast).findFirst().orElseThrow().mode()).isEqualTo("GUIDEE");

        // Passé la date de fin, la solution complète redevient visible.
        horloge.avancer(Duration.ofDays(3));
        vigie(YASSINE, q).andExpect(jsonPath("$.data.principale.texte", containsString("[1]")));
    }

    // ---------------------------------------------------------------- Niveau 3 : réponse générale

    @Test
    void laReponseGeneraleSeDemandeApresLAbstentionEtPrevientLeFormateur() throws Exception {
        long q = publier(YASSINE, questionSur(HORS_COURS));

        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/vigie/general", q))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.niveau").value("GENERAL"))
                .andExpect(jsonPath("$.data.texte").value(FauxIa.GENERALE))
                .andExpect(jsonPath("$.data.sources", hasSize(0)))
                .andExpect(jsonPath("$.data.confiance").doesNotExist())
                .andExpect(jsonPath("$.data.incertitudes", hasSize(1)))
                .andExpect(jsonPath("$.data.avertissement", containsString("hors cours, non vérifiée")));
        assertThat(typesNotifies(FORMATRICE)).contains(TypeNotification.DEMANDE_GENERALE);

        // Une seule génération : la seconde demande renvoie la même réponse ; les deux restent séparées dans le fil.
        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/vigie/general", q)).andExpect(status().isOk());
        assertThat(ia.corps.stream().filter(RepondreIaDto.RequeteGenerale.class::isInstance).count()).isEqualTo(1);
        vigie(SARA, q).andExpect(jsonPath("$.data.principale.statut").value("HORS_COURS"))
                .andExpect(jsonPath("$.data.generale.niveau").value("GENERAL"))
                .andExpect(jsonPath("$.data.generaleDisponible").value(false));
        // Jamais dans la base de connaissances du forum sans validation.
        assertThat(passagesForum.count()).isZero();
    }

    @Test
    void laReponseGeneraleNEstJamaisProposeeSiLeCoursRepond() throws Exception {
        long q = publier(YASSINE, questionDuCours());
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/vigie/general", q)).andExpect(status().isConflict());
        assertThat(ia.corps.stream().filter(RepondreIaDto.RequeteGenerale.class::isInstance)).isEmpty();
    }

    @Test
    void laReponseGeneraleEstRefuseeSiLeFormateurLaDesactive() throws Exception {
        long q = publier(YASSINE, questionSur(HORS_COURS));
        reglerCours(p -> p.setReponseGeneraleAutorisee(false));
        vigie(YASSINE, q).andExpect(jsonPath("$.data.generaleDisponible").value(false));
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/vigie/general", q))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("désactivé la réponse générale")));
    }

    @Test
    void laReponseGeneraleEstRefuseeEnModeExamen() throws Exception {
        long q = publier(YASSINE, questionSur(HORS_COURS));
        reglerCours(p -> p.setModeExamen(true));
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/vigie/general", q))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Mode examen")));
        assertThat(ia.corps.stream().filter(RepondreIaDto.RequeteGenerale.class::isInstance)).isEmpty();
    }

    @Test
    void lAdminPeutCouperLaReponseGeneraleSurToutLaPlateforme() throws Exception {
        long q = publier(YASSINE, questionSur(HORS_COURS));
        parametres.modifier(Map.of(ParametresService.REPONSE_GENERALE, "false"), ADMIN);
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/vigie/general", q))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("plateforme")));
    }

    // ---------------------------------------------------------------- Décisions du formateur

    @Test
    void seulLeFormateurDuCoursOuLAdminValideUneReponse() throws Exception {
        long q = publier(YASSINE, questionDuCours());
        long r = idVigie(q);
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/{id}/valider", r)).andExpect(status().isForbidden());
        en(AUTRE_FORMATEUR, ENSEIGNANT, post("/api/forum/vigie/{id}/valider", r)).andExpect(status().isForbidden());

        en(FORMATRICE, ENSEIGNANT, post("/api/forum/vigie/{id}/valider", r))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.data.verifieeParFormateur").value(true));
        // La réponse validée rejoint la base de connaissances du forum (deuxième source de La Vigie).
        assertThat(passagesForum.count()).isPositive();
        en(ADMIN, ADMIN_ROLE, post("/api/forum/vigie/{id}/valider", r)).andExpect(status().isOk());
    }

    @Test
    void uneCorrectionRemplaceLeTexteSansPerdreLOriginal() throws Exception {
        long q = publier(YASSINE, questionDuCours());
        long r = idVigie(q);
        String original = reponsesIa.findById(r).orElseThrow().getTexte();

        en(FORMATRICE, ENSEIGNANT, post("/api/forum/vigie/{id}/corriger", r),
                Map.of("texte", "Ta condition sur la table de droite doit aller dans le ON, pas dans le WHERE."))
                .andExpect(status().isOk());
        vigie(YASSINE, q)
                .andExpect(jsonPath("$.data.principale.statut").value("CORRIGEE"))
                .andExpect(jsonPath("$.data.principale.texte", startsWith("Ta condition")))
                .andExpect(jsonPath("$.data.principale.texteCorrige").doesNotExist())
                .andExpect(jsonPath("$.data.principale.verifieeParFormateur").value(true));
        vigie(FORMATRICE, q)
                .andExpect(jsonPath("$.data.principale.texte").value(original))
                .andExpect(jsonPath("$.data.principale.texteCorrige", startsWith("Ta condition")));
        assertThat(passagesForum.findAll()).anyMatch(p -> p.getTexte().contains("dans le ON"));
    }

    @Test
    void uneReponseRejeteeDisparaitPourLesApprenants() throws Exception {
        long q = publier(YASSINE, questionDuCours());
        long r = idVigie(q);
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/vigie/{id}/rejeter", r), Map.of("motif", "Hors sujet"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("REJETEE"));
        vigie(YASSINE, q).andExpect(jsonPath("$.data.principale").doesNotExist());
        vigie(FORMATRICE, q).andExpect(jsonPath("$.data.principale.statut").value("REJETEE"));
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/{id}/avis", r), Map.of("utile", true))
                .andExpect(status().isNotFound());
    }

    @Test
    void uneAbstentionNeSeValidePas() throws Exception {
        long q = publier(YASSINE, questionSur(HORS_COURS));
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/vigie/{id}/valider", idVigie(q)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lAvisUtileEstUniqueModifiableEtRapporteUnPointALAuteur() throws Exception {
        long q = publier(YASSINE, questionDuCours());
        long r = idVigie(q);
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/{id}/avis", r), Map.of("utile", true))
                .andExpect(jsonPath("$.data.utileOui").value(1)).andExpect(jsonPath("$.data.monAvis").value(true));
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/{id}/avis", r), Map.of("utile", true))
                .andExpect(jsonPath("$.data.utileOui").value(1));
        en(YASSINE, ETUDIANT, get("/api/forum/moi/reputation")).andExpect(jsonPath("$.data.points").value(1));

        en(YASSINE, ETUDIANT, post("/api/forum/vigie/{id}/avis", r), Map.of("utile", false))
                .andExpect(jsonPath("$.data.utileOui").value(0)).andExpect(jsonPath("$.data.utileNon").value(1));
        en(YASSINE, ETUDIANT, get("/api/forum/moi/reputation")).andExpect(jsonPath("$.data.points").value(0));
        en(SARA, ETUDIANT, post("/api/forum/vigie/{id}/avis", r), Map.of("utile", true))
                .andExpect(jsonPath("$.data.utileOui").value(1));
    }

    // ---------------------------------------------------------------- Assistant privé

    @Test
    void lAssistantRepondEnPriveAvecSourcesEtResteInvisibleAuxAutres() throws Exception {
        long c = conversation(YASSINE);
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/conversations/{id}/messages", c),
                Map.of("texte", DANS_LE_COURS + " " + PRECISION, "mode", "DIRECTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("VIGIE"))
                .andExpect(jsonPath("$.data.reponse.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.reponse.sources[0].origine").value("COURS"))
                .andExpect(jsonPath("$.data.reponse.confiance").value(FauxIa.CONFIANCE));

        en(YASSINE, ETUDIANT, get("/api/forum/vigie/conversations/{id}", c))
                .andExpect(jsonPath("$.data.messages", hasSize(2)))
                .andExpect(jsonPath("$.data.titre", startsWith(DANS_LE_COURS)));
        en(SARA, ETUDIANT, get("/api/forum/vigie/conversations/{id}", c)).andExpect(status().isNotFound());
        en(SARA, ETUDIANT, post("/api/forum/vigie/conversations/{id}/messages", c),
                Map.of("texte", "Et moi ?", "mode", "DIRECTE")).andExpect(status().isNotFound());
        en(SARA, ETUDIANT, get("/api/forum/vigie/conversations")).andExpect(jsonPath("$.data", hasSize(0)));
        // Rien n'est publié dans le forum.
        en(SARA, ETUDIANT, get("/api/forum/discussions")).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void uneQuestionDeSuiviEstCondenseeAvecLHistorique() throws Exception {
        long c = conversation(YASSINE);
        envoyer(YASSINE, c, DANS_LE_COURS + " " + PRECISION, "DIRECTE");
        envoyer(YASSINE, c, "et avec un RIGHT JOIN ?", "DIRECTE");

        ReformulerIaDto.Requete suivi = ia.corps.stream().filter(ReformulerIaDto.Requete.class::isInstance)
                .map(ReformulerIaDto.Requete.class::cast).filter(r -> r.question().contains("RIGHT JOIN"))
                .findFirst().orElseThrow();
        assertThat(suivi.historique()).hasSize(2);
        assertThat(suivi.historique().get(0).texte()).startsWith(DANS_LE_COURS);
        assertThat(suivi.historique().get(1).role()).isEqualTo("VIGIE");
    }

    @Test
    void enModeExamenLAssistantGuideEtRefuseLaReponseGenerale() throws Exception {
        long c = conversation(YASSINE);
        reglerCours(p -> p.setModeExamen(true));
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/conversations/{id}/messages", c),
                Map.of("texte", DANS_LE_COURS + " " + PRECISION, "mode", "DIRECTE"))
                .andExpect(jsonPath("$.data.reponse.mode").value("GUIDEE"))
                .andExpect(jsonPath("$.data.reponse.texte").doesNotExist())
                .andExpect(jsonPath("$.data.reponse.indices", hasSize(2)));
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/conversations/{id}/messages", c),
                Map.of("texte", HORS_COURS, "mode", "GENERAL")).andExpect(status().isForbidden());
    }

    @Test
    void lAssistantDonneUneReponseGeneraleSurDemandeHorsExamen() throws Exception {
        long c = conversation(YASSINE);
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/conversations/{id}/messages", c),
                Map.of("texte", HORS_COURS, "mode", "GENERAL"))
                .andExpect(jsonPath("$.data.reponse.niveau").value("GENERAL"))
                .andExpect(jsonPath("$.data.reponse.confiance").doesNotExist())
                .andExpect(jsonPath("$.data.reponse.avertissement", containsString("hors cours")));
    }

    @Test
    void publierCommeQuestionJointLaReponseDejaObtenueAuFil() throws Exception {
        long c = conversation(YASSINE);
        envoyer(YASSINE, c, DANS_LE_COURS + " " + PRECISION, "DIRECTE");
        MvcResult brouillon = en(YASSINE, ETUDIANT, post("/api/forum/vigie/conversations/{id}/publier", c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coursId").value(COURS))
                .andExpect(jsonPath("$.data.titre", startsWith(DANS_LE_COURS)))
                .andExpect(jsonPath("$.data.contenu", containsString("La Vigie")))
                .andReturn();
        String json = brouillon.getResponse().getContentAsString();
        Map<String, Object> corps = question(COURS, JsonPath.read(json, "$.data.titre"));
        corps.put("contenu", JsonPath.read(json, "$.data.contenu"));
        corps.put("conversationId", c);
        long appelsAvant = appelsRepondre();

        long q = publier(YASSINE, corps);
        vigie(YASSINE, q).andExpect(jsonPath("$.data.principale.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.principale.sources[0].origine").value("COURS"));
        assertThat(appelsRepondre()).isEqualTo(appelsAvant);
    }

    @Test
    void laConversationDUnAutreNeSeJointPasAUnFil() throws Exception {
        long c = conversation(SARA);
        envoyer(SARA, c, DANS_LE_COURS + " " + PRECISION, "DIRECTE");
        Map<String, Object> corps = questionDuCours();
        corps.put("conversationId", c);
        long appelsAvant = appelsRepondre();
        publier(YASSINE, corps);
        // Pas de copie de la réponse de Sara : La Vigie génère une réponse propre au fil.
        assertThat(appelsRepondre()).isEqualTo(appelsAvant + 1);
    }

    @Test
    void lAssistantEstFermeSiLaVigieEstDesactiveeDansLeCours() throws Exception {
        reglerCours(p -> p.setVigieActive(false));
        en(YASSINE, ETUDIANT, post("/api/forum/vigie/conversations"), Map.of("coursId", COURS))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Aides

    private static Map<String, Object> questionDuCours() {
        Map<String, Object> corps = question(COURS, DANS_LE_COURS);
        corps.put("contenu", PRECISION);
        return corps;
    }

    private static Map<String, Object> questionSur(String titre) {
        Map<String, Object> corps = question(COURS, titre);
        corps.put("contenu", "Je voudrais publier mon projet en production, avec plusieurs conteneurs.");
        corps.put("etiquettes", List.of("deploiement"));
        return corps;
    }

    private org.springframework.test.web.servlet.ResultActions vigie(long userId, long discussionId) throws Exception {
        return en(userId, role(userId), get("/api/forum/discussions/{id}/vigie", discussionId))
                .andExpect(status().isOk());
    }

    private long idVigie(long discussionId) throws Exception {
        String json = vigie(FORMATRICE, discussionId).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.principale.id")).longValue();
    }

    private long conversation(long userId) throws Exception {
        MvcResult r = en(userId, role(userId), post("/api/forum/vigie/conversations"), Map.of("coursId", COURS))
                .andExpect(status().isCreated()).andReturn();
        return id(r);
    }

    private void envoyer(long userId, long conversationId, String texte, String mode) throws Exception {
        en(userId, role(userId), post("/api/forum/vigie/conversations/{id}/messages", conversationId),
                Map.of("texte", texte, "mode", mode)).andExpect(status().isOk());
    }

    private long appelsRepondre() {
        return ia.corps.stream().filter(RepondreIaDto.Requete.class::isInstance).count();
    }

    private List<TypeNotification> typesNotifies(long userId) {
        return notifications.findAll().stream().filter(n -> n.getDestinataireId().equals(userId))
                .map(n -> n.getType()).toList();
    }

    private void reglerCours(java.util.function.Consumer<ParametresForumCours> reglage) {
        ParametresForumCours p = parametres.cours(COURS);
        reglage.accept(p);
        parametresCours.save(p);
    }
}

package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.entity.TraitementIA;
import tn.esprit.bawsla.cours.repository.AudioRevisionRepository;
import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.ExplicationAudioRepository;
import tn.esprit.bawsla.cours.repository.FicheRevisionRepository;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;
import tn.esprit.bawsla.cours.repository.ResumeLocalRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;

/**
 * Résumé automatique local : génération asynchrone (TEXTE, RESUME, AUDIO), droits, 200 sans data, visibilité de l'apprenant,
 * validation, historique, langue transmise à la voix, délai de lecture propre à l'appel. L'IA est remplacée par {@link FauxIa}.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cours_resume_local;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ResumeLocalApiTest.IaDeTest.class)
class ResumeLocalApiTest {

    private static final Path STOCKAGE = dossierTemporaire();

    private static final long FORMATEUR_A = 10L;
    private static final long FORMATEUR_B = 20L;
    private static final long APPRENANT = 30L;
    private static final long ADMIN = 1L;

    private static final String JETON = "Bearer jeton-de-test";
    private static final String CONTENU = "An observer defines a one-to-many dependency between objects so that all dependents are notified. "
            + "A strategy lets the algorithm vary independently from the clients that use it. "
            + "Contact the teacher at prof@exemple.tn for any question about this chapter of the software design course.";

    @TestConfiguration
    static class IaDeTest {
        @Bean
        @Primary
        FauxIa fauxIa() {
            return new FauxIa();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired FauxIa ia;
    @Autowired CoursRepository coursRepository;
    @Autowired PageDocumentRepository pageRepository;
    @Autowired FicheRevisionRepository ficheRepository;
    @Autowired AudioRevisionRepository audioRepository;
    @Autowired ExplicationAudioRepository explicationRepository;
    @Autowired ResumeLocalRepository resumeRepository;
    @Autowired TraitementIARepository traitementRepository;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("cours.stockage.dossier", STOCKAGE::toString);
    }

    @BeforeEach
    void nettoyer() throws IOException {
        ia.reinitialiser();
        traitementRepository.deleteAll();
        resumeRepository.deleteAll();
        explicationRepository.deleteAll();
        audioRepository.deleteAll();
        ficheRepository.deleteAll();
        pageRepository.deleteAll();
        coursRepository.deleteAll();
        if (Files.exists(STOCKAGE)) {
            try (Stream<Path> chemins = Files.walk(STOCKAGE)) {
                chemins.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(STOCKAGE)).forEach(p -> p.toFile().delete());
            }
        }
    }

    // ------------------------------------------------------------------ génération

    @Test
    void generationEnArrierePlanTroisEtapesPuisResumeEtAudio() throws Exception {
        long id = coursPublie(FORMATEUR_A);

        generer(FORMATEUR_A, id, "")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.etapes.length()").value(3))
                .andExpect(jsonPath("$.data.etapes[0].etape").value("TEXTE"))
                .andExpect(jsonPath("$.data.etapes[1].etape").value("RESUME"))
                .andExpect(jsonPath("$.data.etapes[2].etape").value("AUDIO"));
        attendreFin(id);

        envoyer(get(url(id, "/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.enCours").value(false))
                .andExpect(jsonPath("$.data.etapes[1].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[2].statut").value("TERMINE"));
        envoyer(get(url(id, "")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("GENERE"))
                .andExpect(jsonPath("$.data.langue").value("en"))
                .andExpect(jsonPath("$.data.modele").value("sshleifer/distilbart-cnn-12-6"))
                .andExpect(jsonPath("$.data.modeUtilise").value("abstractif"))
                .andExpect(jsonPath("$.data.scoreFidelite").value(72))
                .andExpect(jsonPath("$.data.alertes[0]").value(containsString("ne ressemblent")))
                .andExpect(jsonPath("$.data.dureeCalculMs").value(4200))
                .andExpect(jsonPath("$.data.phrases.length()").value(2))
                .andExpect(jsonPath("$.data.phrases[0].page").value(39))
                .andExpect(jsonPath("$.data.phrases[0].extraitSource").value(containsString("Observer")))
                .andExpect(jsonPath("$.data.phrases[1].justifiee").value(false))
                .andExpect(jsonPath("$.data.aAudio").value(true))
                .andExpect(jsonPath("$.data.dureeAudio").value(12500))
                .andExpect(jsonPath("$.data.fournisseurAudio").value("elevenlabs"))
                .andExpect(jsonPath("$.data.valideeDisponible").value(false));

        var demande = ia.demandesResumeLocal.get(0);
        assertThat(demande.longueur()).isEqualTo("moyenne");
        assertThat(demande.mode()).isEqualTo("auto");
        assertThat(demande.pages()).isNotEmpty();
        assertThat(demande.pages().get(0).texte()).contains("observer").doesNotContain("prof@exemple.tn");
        // La voix reçoit le résumé et la langue DÉTECTÉE par l'IA (ici l'anglais), pas le français par défaut.
        assertThat(ia.demandesAudio).hasSize(1);
        assertThat(ia.demandesAudio.get(0).langue()).isEqualTo("en");
        assertThat(ia.demandesAudio.get(0).texte()).isEqualTo("The observer pattern defines a one-to-many dependency. Strategy lets algorithms vary.");
        // Le Authorization est lu avant l'async et transmis à chaque appel (résumé puis voix).
        assertThat(ia.autorisations).hasSize(2).containsOnly(JETON);
        // Délai de lecture propre à l'appel (300 s : le premier chargement du modèle est long).
        assertThat(ia.delais).containsExactly("resume=300000");
    }

    @Test
    void longueurEtModeTransmisEtValides() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        generer(FORMATEUR_A, id, "?longueur=longue&mode=extractif").andExpect(status().isAccepted());
        attendreFin(id);
        assertThat(ia.demandesResumeLocal.get(0).longueur()).isEqualTo("longue");
        assertThat(ia.demandesResumeLocal.get(0).mode()).isEqualTo("extractif");

        generer(FORMATEUR_A, id, "?longueur=enorme").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("courte, moyenne ou longue")));
        generer(FORMATEUR_A, id, "?mode=magique").andExpect(status().isBadRequest());
        assertThat(ia.demandesResumeLocal).hasSize(1);
    }

    @Test
    void modeExtractifAfficheLeScoreDuReformuleRemplace() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        ia.modeResume = "extractif";
        generer(FORMATEUR_A, id, "").andExpect(status().isAccepted());
        attendreFin(id);
        envoyer(get(url(id, "")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.modeUtilise").value("extractif"))
                .andExpect(jsonPath("$.data.modele").value("extractif-textrank"))
                .andExpect(jsonPath("$.data.scoreReformule").value(31))
                .andExpect(jsonPath("$.data.alertes[0]").value(containsString("remplacé par les phrases clés")));
    }

    @Test
    void coursVideOuPdfScanneErreurClaireSansAppelALIA() throws Exception {
        long vide = creerCours(FORMATEUR_A, "Sans texte");
        generer(FORMATEUR_A, vide, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("pas assez de texte")));
        long scanne = creerCours(FORMATEUR_A, "Scanné");
        creerChapitre(FORMATEUR_A, scanne, "Intro", "court");
        envoyerPdf(FORMATEUR_A, scanne, "scanne.pdf");
        generer(FORMATEUR_A, scanne, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("scanné")));
        assertThat(traitementRepository.count()).isZero();
        assertThat(ia.demandesResumeLocal).isEmpty();
    }

    @Test
    void unSeulTraitementALaFois() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        CountDownLatch verrou = new CountDownLatch(1);
        ia.blocageResumeLocal = verrou;
        generer(FORMATEUR_A, id, "").andExpect(status().isAccepted());
        generer(FORMATEUR_A, id, "").andExpect(status().isConflict());
        envoyer(get(url(id, "/traitement")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.enCours").value(true));
        verrou.countDown();
        attendreFin(id);
        generer(FORMATEUR_A, id, "").andExpect(status().isAccepted());
        attendreFin(id);
    }

    @Test
    void lePlusRecentResteListeEtLHistoriqueEstConserve() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        genererEtAttendre(id);
        genererEtAttendre(id);
        assertThat(resumeRepository.findByCoursId(id)).hasSize(2);
    }

    // ------------------------------------------------------------------ droits

    @Test
    void seulLeProprietaireOuLAdminAgit() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        genererEtAttendre(id);
        for (long intrus : new long[] {FORMATEUR_B, APPRENANT}) {
            String role = intrus == APPRENANT ? "ETUDIANT" : "ENSEIGNANT";
            envoyer(post(url(id, "/generer")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(get(url(id, "/traitement")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(patch(url(id, "/valider")), intrus, role, null).andExpect(status().isForbidden());
        }
        assertThat(ia.demandesResumeLocal).hasSize(1);
        envoyer(post(url(id, "/generer")), ADMIN, "ADMIN", null).andExpect(status().isAccepted());
        attendreFin(id);
        envoyer(patch(url(id, "/valider")), ADMIN, "ADMIN", null).andExpect(status().isOk());
    }

    @Test
    void sansResumeLaReponseEst200SansDataEtLeCoursInvisibleResteUn404() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        for (long qui : new long[] {FORMATEUR_A, APPRENANT, ADMIN}) {
            String role = qui == APPRENANT ? "ETUDIANT" : qui == ADMIN ? "ADMIN" : "ENSEIGNANT";
            envoyer(get(url(id, "")), qui, role, null).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        }
        envoyer(get(url(id, "/audio")), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(patch(url(id, "/valider")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isNotFound());
        long brouillon = creerCours(FORMATEUR_A, "Brouillon");
        envoyer(get(url(brouillon, "")), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(get(url(999_999, "")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ visibilité de l'apprenant et validation

    @Test
    void l_apprenant_ne_voit_que_la_version_validee_sans_les_controles() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        genererEtAttendre(id);

        // Avant validation : rien, ni texte ni audio.
        envoyer(get(url(id, "")), APPRENANT, "ETUDIANT", null).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        envoyer(get(url(id, "/audio")), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());

        envoyer(patch(url(id, "/valider")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("VALIDE"));

        envoyer(get(url(id, "")), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("VALIDE"))
                .andExpect(jsonPath("$.data.langue").value("en"))
                .andExpect(jsonPath("$.data.texte").value(containsString("observer pattern")))
                .andExpect(jsonPath("$.data.phrases[0].page").value(39))
                .andExpect(jsonPath("$.data.aAudio").value(true))
                // Ni score, ni alertes, ni modèle, ni extrait, ni verdict par phrase, ni durée de calcul : réservés au formateur.
                .andExpect(jsonPath("$.data.scoreFidelite").doesNotExist())
                .andExpect(jsonPath("$.data.scoreReformule").doesNotExist())
                .andExpect(jsonPath("$.data.alertes").doesNotExist())
                .andExpect(jsonPath("$.data.modele").doesNotExist())
                .andExpect(jsonPath("$.data.dureeCalculMs").doesNotExist())
                .andExpect(jsonPath("$.data.valideeDisponible").doesNotExist())
                .andExpect(jsonPath("$.data.phrases[0].extraitSource").doesNotExist())
                .andExpect(jsonPath("$.data.phrases[0].justifiee").doesNotExist());
        byte[] audio = envoyer(get(url(id, "/audio")), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk()).andExpect(content().contentType("audio/mpeg")).andReturn().getResponse().getContentAsByteArray();
        assertThat(audio).isEqualTo(FauxIa.MP3);
    }

    @Test
    void uneNouvelleGenerationNeChangeRienPourLApprenantTantQuElleNEstPasValidee() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        genererEtAttendre(id);
        envoyer(patch(url(id, "/valider")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        ia.modeResume = "extractif";
        genererEtAttendre(id);

        envoyer(get(url(id, "")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.statut").value("GENERE")).andExpect(jsonPath("$.data.modeUtilise").value("extractif"))
                .andExpect(jsonPath("$.data.valideeDisponible").value(true));
        envoyer(get(url(id, "")), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.statut").value("VALIDE")).andExpect(jsonPath("$.data.modeUtilise").value("abstractif"));
        envoyer(patch(url(id, "/valider")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        envoyer(get(url(id, "")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.modeUtilise").value("extractif"));
    }

    @Test
    void validerDeuxFoisNeChangeRien() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        genererEtAttendre(id);
        envoyer(patch(url(id, "/valider")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        envoyer(patch(url(id, "/valider")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("VALIDE"));
        assertThat(ia.demandesAudio).hasSize(1); // l'audio existe : aucun nouvel appel à la voix
    }

    // ------------------------------------------------------------------ pannes

    @Test
    void echecDuResumeAnnuleLAudioEtLeMessageEstLisible() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        ia.panneResumeLocal = new ServiceUnavailableException("Résumé local indisponible sur ce serveur");
        generer(FORMATEUR_A, id, "").andExpect(status().isAccepted());
        attendreFin(id);
        envoyer(get(url(id, "/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[0].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[1].statut").value("ECHEC"))
                .andExpect(jsonPath("$.data.etapes[1].erreur").value("Résumé local indisponible sur ce serveur"))
                .andExpect(jsonPath("$.data.etapes[2].statut").value("ANNULE"));
        envoyer(get(url(id, "")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data").doesNotExist());
        assertThat(ia.demandesAudio).isEmpty();

        ia.panneResumeLocal = new IllegalStateException("détail technique secret");
        generer(FORMATEUR_A, id, "").andExpect(status().isAccepted());
        attendreFin(id);
        envoyer(get(url(id, "/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret"))));
    }

    @Test
    void echecDeLAudioLaisseLeResumeEtValiderRelanceLAudio() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        ia.panneAudio = new ServiceUnavailableException("La synthèse vocale est indisponible pour le moment.");
        genererEtAttendre(id);
        envoyer(get(url(id, "/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[1].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[2].statut").value("ECHEC"))
                .andExpect(jsonPath("$.data.etapes[2].erreur").value(containsString("synthèse")));
        envoyer(get(url(id, "")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.aAudio").value(false)).andExpect(jsonPath("$.data.phrases.length()").value(2));

        // La panne passe : valider produit l'audio manquant en arrière-plan.
        ia.panneAudio = null;
        envoyer(patch(url(id, "/valider")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        attendreFin(id);
        envoyer(get(url(id, "")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.aAudio").value(true));
        envoyer(get(url(id, "/audio")), APPRENANT, "ETUDIANT", null).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ nettoyage

    @Test
    void supprimerUnCoursSupprimeSesResumesEtSesAudios() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        genererEtAttendre(id);
        genererEtAttendre(id);
        assertThat(fichiers("audio")).hasSize(2);
        envoyer(delete("/api/cours/" + id), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        assertThat(fichiers("audio")).isEmpty();
        assertThat(resumeRepository.findByCoursId(id)).isEmpty();
        assertThat(traitementRepository.findByCoursId(id)).isEmpty();
    }

    // ------------------------------------------------------------------ aides

    private static String url(long coursId, String route) {
        return "/api/cours/" + coursId + "/resume-local" + route;
    }

    private ResultActions generer(long utilisateur, long coursId, String requete) throws Exception {
        return envoyer(post(url(coursId, "/generer") + requete), utilisateur, "ENSEIGNANT", null);
    }

    private void genererEtAttendre(long id) throws Exception {
        generer(FORMATEUR_A, id, "").andExpect(status().isAccepted());
        attendreFin(id);
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder requete, long id, String role, String corps) throws Exception {
        requete.header("X-User-Id", id).header("X-User-Role", role).header("Authorization", JETON);
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mvc.perform(requete);
    }

    private long creerCours(long formateur, String titre) throws Exception {
        String corps = "{\"titre\":\"" + titre + "\",\"categorie\":\"Données\",\"niveau\":\"DEBUTANT\"}";
        return identifiant(envoyer(post("/api/cours"), formateur, "ENSEIGNANT", corps).andExpect(status().isCreated()));
    }

    private void creerChapitre(long formateur, long coursId, String titre, String contenu) throws Exception {
        String corps = "{\"titre\":\"" + titre + "\",\"contenu\":\"" + contenu + "\",\"dureeMinutes\":15}";
        envoyer(post("/api/cours/" + coursId + "/chapitres"), formateur, "ENSEIGNANT", corps).andExpect(status().isCreated());
    }

    /** Cours publié (visible des apprenants) dont l'unique chapitre contient assez de texte pour être résumé. */
    private long coursPublie(long formateur) throws Exception {
        long id = creerCours(formateur, "Design patterns");
        creerChapitre(formateur, id, "Introduction", CONTENU);
        envoyer(patch("/api/cours/" + id + "/statut"), formateur, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}").andExpect(status().isOk());
        return id;
    }

    private void envoyerPdf(long formateur, long coursId, String ressource) throws Exception {
        byte[] octets = new ClassPathResource(ressource).getContentAsByteArray();
        mvc.perform(multipart("/api/cours/" + coursId + "/pdf").file(new MockMultipartFile("fichier", ressource, "application/pdf", octets))
                .header("X-User-Id", formateur).header("X-User-Role", "ENSEIGNANT")).andExpect(status().isOk());
    }

    private static long identifiant(ResultActions actions) throws Exception {
        Number n = JsonPath.read(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
        return n.longValue();
    }

    /** Attend la fin du dernier traitement du résumé local (au plus 15 s) : il s'exécute dans un autre thread. */
    private void attendreFin(long coursId) throws Exception {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            var dernier = traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.PORTEE_RESUME_LOCAL);
            if (dernier.isPresent()) {
                var lignes = traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.get().getExecution());
                if (lignes.stream().noneMatch(l -> l.getStatut().actif())) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Le traitement n'est pas terminé après 15 s");
    }

    private static List<Path> fichiers(String sousDossier) throws IOException {
        Path dossier = STOCKAGE.resolve(sousDossier);
        if (!Files.exists(dossier)) {
            return List.of();
        }
        try (Stream<Path> chemins = Files.list(dossier)) {
            return chemins.toList();
        }
    }

    private static Path dossierTemporaire() {
        try {
            return Files.createTempDirectory("bawsla-cours-resume-local-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

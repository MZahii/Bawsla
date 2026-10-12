package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import tn.esprit.bawsla.cours.entity.FicheRevision;
import tn.esprit.bawsla.cours.entity.LangueExplication;
import tn.esprit.bawsla.cours.entity.ModeExplication;
import tn.esprit.bawsla.cours.entity.Notion;
import tn.esprit.bawsla.cours.entity.StatutFiche;
import tn.esprit.bawsla.cours.entity.TraitementIA;
import tn.esprit.bawsla.cours.repository.AudioRevisionRepository;
import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.ExplicationAudioRepository;
import tn.esprit.bawsla.cours.repository.FicheRevisionRepository;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;

/**
 * Explication orale par l'IA : génération asynchrone, droits, visibilité de l'apprenant, aperçu audio, règle « pas de validation sans
 * aperçu », audio refait si le texte change, anglais = traduction du français validé, délais par appel, erreurs et nettoyage.
 * L'IA est remplacée par {@link FauxIa} (aucun appel réseau).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cours_explication;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ExplicationApiTest.IaDeTest.class)
class ExplicationApiTest {

    private static final Path STOCKAGE = dossierTemporaire();

    private static final long FORMATEUR_A = 10L;
    private static final long FORMATEUR_B = 20L;
    private static final long APPRENANT = 30L;
    private static final long ADMIN = 1L;

    private static final String JETON = "Bearer jeton-de-test";
    private static final String CONTENU = "Une jointure interne ne garde que les lignes qui ont une correspondance dans les deux tables. "
            + "Une jointure externe gauche garde toutes les lignes de la table de gauche, même sans correspondance. "
            + "Contactez le formateur à prof@exemple.tn pour toute question sur ce chapitre du cours de bases de données.";

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
    @Autowired TraitementIARepository traitementRepository;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("cours.stockage.dossier", STOCKAGE::toString);
    }

    @BeforeEach
    void nettoyer() throws IOException {
        ia.reinitialiser();
        traitementRepository.deleteAll();
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

    // ------------------------------------------------------------------ génération du script

    @Test
    void generationDuScriptFrancaisEnArrierePlan() throws Exception {
        long id = coursPublie(FORMATEUR_A);

        generer(FORMATEUR_A, id, "fr", "monologue")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.etapes.length()").value(2))
                .andExpect(jsonPath("$.data.etapes[0].etape").value("TEXTE"))
                .andExpect(jsonPath("$.data.etapes[1].etape").value("SCRIPT"));
        attendreFin(id, "fr", "monologue");

        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.langue").value("fr"))
                .andExpect(jsonPath("$.data.mode").value("monologue"))
                .andExpect(jsonPath("$.data.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.segments.length()").value(6))
                .andExpect(jsonPath("$.data.segments[2].type").value("analogie"))
                .andExpect(jsonPath("$.data.segments[2].analogie").value(true))
                .andExpect(jsonPath("$.data.segments[1].extraitSource").value("ne garde que les lignes"))
                .andExpect(jsonPath("$.data.segments[1].page").value(1))
                .andExpect(jsonPath("$.data.scoreFidelite").value(92))
                .andExpect(jsonPath("$.data.versionPrompt").value("explication_v1"))
                .andExpect(jsonPath("$.data.aAudio").value(false))
                .andExpect(jsonPath("$.data.aPuEcouter").value(false))
                .andExpect(jsonPath("$.data.valideeDisponible").value(false))
                // Pas de fiche validée : le plan est vide, et l'alerte « notions choisies par l'IA » s'affiche.
                .andExpect(jsonPath("$.data.notionsChoisiesParIa").value(true))
                .andExpect(jsonPath("$.data.alertes[0]").value(containsString("Notions choisies par l'IA")));

        var demande = ia.demandesScript.get(0);
        assertThat(demande.mode()).isEqualTo("monologue");
        assertThat(demande.langue()).isEqualTo("fr");
        assertThat(demande.dureeMinutes()).isEqualTo(4);
        assertThat(demande.notions()).isEmpty();
        assertThat(demande.niveau().name()).isEqualTo("DEBUTANT");
        assertThat(demande.pages()).hasSize(1); // un cours sans PDF : un « page » par chapitre
        assertThat(demande.pages().get(0).texte()).contains("Chapitre 1").contains("jointure interne").doesNotContain("prof@exemple.tn").contains("[EMAIL]");
    }

    @Test
    void leAuthorizationEstCapturePuisTransmisAUnThreadSansRequete() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        generer(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");
        apercu(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");
        // Le script et l'audio sont produits par l'exécuteur asynchrone, qui ne voit plus la requête HTTP.
        assertThat(ia.autorisations).hasSize(2).containsOnly(JETON);
    }

    @Test
    void leDelaiDeLectureEstPropreAChaqueAppel() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        generer(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");
        apercu(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");
        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", correction("Texte corrigé")).andExpect(status().isOk());
        // Script : 2 x 150 s + marge ; audio : plusieurs appels vocaux ; contrôle de fidélité : sans LLM, court.
        assertThat(ia.delais).containsExactly("script=330000", "audio=420000", "fidelite=30000");
    }

    @Test
    void lePlanNEnvoieQueLesTitresDeLaFicheValidee() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        FicheRevision fiche = new FicheRevision();
        fiche.setCoursId(id);
        fiche.setResume("Un résumé que l'IA ne doit jamais recevoir.");
        fiche.setStatut(StatutFiche.VALIDEE);
        for (String titre : List.of("INNER JOIN", "LEFT JOIN")) {
            Notion n = new Notion();
            n.setFiche(fiche);
            n.setTitre(titre);
            n.setDefinition("Définition secrète de " + titre);
            fiche.getNotions().add(n);
        }
        ficheRepository.save(fiche);
        // Une fiche plus récente mais pas validée ne sert pas de plan.
        FicheRevision brouillon = new FicheRevision();
        brouillon.setCoursId(id);
        brouillon.setResume("Brouillon");
        Notion nb = new Notion();
        nb.setFiche(brouillon);
        nb.setTitre("Notion du brouillon");
        nb.setDefinition("d");
        brouillon.getNotions().add(nb);
        ficheRepository.save(brouillon);

        generer(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");

        assertThat(ia.demandesScript.get(0).notions()).containsExactly("INNER JOIN", "LEFT JOIN");
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.notionsChoisiesParIa").value(false))
                .andExpect(jsonPath("$.data.alertes").value(not(hasItem(containsString("Notions choisies")))));
    }

    @Test
    void lePdfEstLaSourceEtSesPagesGardentLeurNumero() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        envoyerPdf(FORMATEUR_A, id, "cours-sql.pdf");
        generer(FORMATEUR_A, id, "fr", "dialogue");
        attendreFin(id, "fr", "dialogue");
        var pages = ia.demandesScript.get(0).pages();
        assertThat(pages).extracting("numero").containsExactly(1, 2, 3);
        assertThat(pages.get(2).texte()).doesNotContain("prof@exemple.tn").doesNotContain("22 123 456");
        assertThat(ia.demandesScript.get(0).mode()).isEqualTo("dialogue");
    }

    @Test
    void coursVideOuPdfScanneErreurClaireSansAppelAIA() throws Exception {
        long vide = creerCours(FORMATEUR_A, "Sans texte");
        generer(FORMATEUR_A, vide, "fr", "monologue")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("pas assez de texte")));

        long scanne = creerCours(FORMATEUR_A, "Scanné");
        creerChapitre(FORMATEUR_A, scanne, "Intro", "court");
        envoyerPdf(FORMATEUR_A, scanne, "scanne.pdf");
        generer(FORMATEUR_A, scanne, "fr", "monologue")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("scanné")));

        assertThat(traitementRepository.count()).isZero();
        assertThat(ia.demandesScript).isEmpty();
        assertThat(ia.autorisations).isEmpty();
    }

    @Test
    void parametresInvalides() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        generer(FORMATEUR_A, id, "es", "monologue").andExpect(status().isBadRequest());
        generer(FORMATEUR_A, id, "fr", "conference").andExpect(status().isBadRequest());
        envoyer(post(url(id, "/generer", "fr", "monologue") + "&duree=1"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isBadRequest());
        envoyer(post(url(id, "/generer", "fr", "monologue") + "&duree=9"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isBadRequest());
        envoyer(get(url(id, "", "fr", "x")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isBadRequest());
        assertThat(ia.demandesScript).isEmpty();
    }

    @Test
    void dureeDemandeeTransmiseALIA() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        envoyer(post(url(id, "/generer", "fr", "monologue") + "&duree=3"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id, "fr", "monologue");
        assertThat(ia.demandesScript.get(0).dureeMinutes()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ droits

    @Test
    void seulLeProprietaireOuLAdminAgit() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");
        long avant = ia.demandesScript.size();

        for (long intrus : new long[] {FORMATEUR_B, APPRENANT}) {
            String role = intrus == APPRENANT ? "ETUDIANT" : "ENSEIGNANT";
            envoyer(post(url(id, "/generer", "fr", "monologue")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(post(url(id, "/apercu", "fr", "monologue")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(get(url(id, "/traitement", "fr", "monologue")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(put(url(id, "", "fr", "monologue")), intrus, role, correction("Piraté")).andExpect(status().isForbidden());
            envoyer(patch(url(id, "/valider", "fr", "monologue")), intrus, role, null).andExpect(status().isForbidden());
        }
        assertThat(ia.demandesScript).hasSize((int) avant);

        // L'ADMIN peut tout faire sur le cours d'un autre.
        envoyer(post(url(id, "/apercu", "fr", "monologue")), ADMIN, "ADMIN", null).andExpect(status().isAccepted());
        attendreFin(id, "fr", "monologue");
        envoyer(patch(url(id, "/valider", "fr", "monologue")), ADMIN, "ADMIN", null).andExpect(status().isOk());
    }

    @Test
    void coursIntrouvableOuNonVisibleResteUn404() throws Exception {
        long brouillon = creerCours(FORMATEUR_A, "Brouillon");
        envoyer(get(url(brouillon, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + brouillon + "/explication/disponibles"), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(get(url(999_999, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ visibilité de l'apprenant

    @Test
    void l_apprenant_ne_voit_que_la_version_validee_sans_les_controles() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");

        // Avant validation : rien du tout (200 sans data), ni disponibilité, ni audio.
        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        envoyer(get("/api/cours/" + id + "/explication/disponibles"), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        envoyer(get(url(id, "/audio", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());

        apercu(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");
        // Même avec un aperçu, une version non validée reste invisible pour l'apprenant.
        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data").doesNotExist());
        envoyer(get(url(id, "/audio", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());

        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("VALIDEE"));

        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segments.length()").value(6))
                .andExpect(jsonPath("$.data.segments[1].page").value(1))
                .andExpect(jsonPath("$.data.segments[1].debutMs").value(10000))
                .andExpect(jsonPath("$.data.segments[1].notion").value("INNER JOIN"))
                .andExpect(jsonPath("$.data.aAudio").value(true))
                .andExpect(jsonPath("$.data.fournisseurAudio").value("gemini"))
                // Ni extrait, ni verdict, ni score, ni alertes, ni modèle : réservés au formateur.
                .andExpect(jsonPath("$.data.segments[1].extraitSource").doesNotExist())
                .andExpect(jsonPath("$.data.segments[1].justifiee").doesNotExist())
                .andExpect(jsonPath("$.data.scoreFidelite").doesNotExist())
                .andExpect(jsonPath("$.data.alertes").doesNotExist())
                .andExpect(jsonPath("$.data.modele").doesNotExist())
                .andExpect(jsonPath("$.data.versionPrompt").doesNotExist())
                .andExpect(jsonPath("$.data.notionsChoisiesParIa").doesNotExist());
        byte[] audio = envoyer(get(url(id, "/audio", "fr", "monologue")), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk()).andExpect(content().contentType("audio/mpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(audio).isEqualTo(FauxIa.MP3_EXPLICATION);
        envoyer(get("/api/cours/" + id + "/explication/disponibles"), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].langue").value("fr"))
                .andExpect(jsonPath("$.data[0].mode").value("monologue"))
                .andExpect(jsonPath("$.data[0].aAudio").value(true));
        // Les autres combinaisons n'existent pas pour lui.
        envoyer(get(url(id, "", "fr", "dialogue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data").doesNotExist());
        envoyer(get(url(id, "", "en", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data").doesNotExist());
    }

    // ------------------------------------------------------------------ aperçu audio et validation

    @Test
    void apercuProduitLAudioEtLeMinutage() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");

        apercu(FORMATEUR_A, id, "fr", "monologue")
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.etapes.length()").value(1))
                .andExpect(jsonPath("$.data.etapes[0].etape").value("AUDIO"));
        attendreFin(id, "fr", "monologue");

        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.aAudio").value(true))
                .andExpect(jsonPath("$.data.audioAJour").value(true))
                .andExpect(jsonPath("$.data.aPuEcouter").value(true))
                .andExpect(jsonPath("$.data.fournisseurAudio").value("gemini"))
                .andExpect(jsonPath("$.data.dureeMs").value(60000))
                .andExpect(jsonPath("$.data.minutageEstime").value(true)) // Gemini : un seul appel, minutage au prorata
                .andExpect(jsonPath("$.data.segments[0].debutMs").value(0))
                .andExpect(jsonPath("$.data.segments[0].finMs").value(9500))
                .andExpect(jsonPath("$.data.segments[5].debutMs").value(50000));
        // Le formateur écoute l'aperçu avant toute validation.
        byte[] audio = envoyer(get(url(id, "/audio", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(audio).isEqualTo(FauxIa.MP3_EXPLICATION);
        assertThat(fichiers("audio")).hasSize(1).allSatisfy(f -> assertThat(f.getFileName().toString()).matches("[0-9a-f-]{36}\\.mp3"));
        // Les segments sont envoyés à l'IA dans l'ordre, avec leur locuteur.
        assertThat(ia.demandesAudioExplication.get(0).segments()).extracting("ordre").containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(ia.demandesAudioExplication.get(0).langue()).isEqualTo("fr");
    }

    @Test
    void minutageExactQuandLeFournisseurFaitUnAppelParSegment() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        ia.minutageEstime = false;
        ia.fournisseurExplication = "elevenlabs";
        scriptGenere(id, "fr", "monologue");
        apercu(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.minutageEstime").value(false)).andExpect(jsonPath("$.data.fournisseurAudio").value("elevenlabs"));
    }

    @Test
    void impossibleDeValiderSansApercu() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");
        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("Écoute d'abord un aperçu")));
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.statut").value("GENEREE"));

        apercu(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");
        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
    }

    @Test
    void apercuSansExplicationEstUn404() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        apercu(FORMATEUR_A, id, "fr", "monologue").andExpect(status().isNotFound());
        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isNotFound());
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void laValidationReutiliseLAudioSiLeTexteNAPasChange() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");
        apercu(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");

        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.audioAJour").value(true));
        Thread.sleep(300); // laisse le temps à un éventuel traitement parasite de démarrer
        assertThat(ia.demandesAudioExplication).hasSize(1); // l'aperçu est réutilisé, rien n'est refait
        assertThat(fichiers("audio")).hasSize(1);
    }

    @Test
    void texteModifieApresLApercuLAudioEstRefaitALaValidation() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");
        apercu(FORMATEUR_A, id, "fr", "monologue");
        attendreFin(id, "fr", "monologue");

        // Correction d'une version non validée : modifiée sur place, l'aperçu n'est plus à jour.
        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", correction("Texte retouché par le formateur"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioAJour").value(false))
                .andExpect(jsonPath("$.data.aPuEcouter").value(true))
                .andExpect(jsonPath("$.data.aAudio").value(true)); // l'ancien aperçu reste écoutable par le formateur
        assertThat(explicationRepository.findByCoursId(id)).hasSize(1);

        // À la validation, l'audio est refait ; tant qu'il ne l'est pas, l'apprenant n'a PAS l'ancien audio (il ne correspond plus au texte).
        ia.blocageAudioExplication = new CountDownLatch(1);
        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("VALIDEE")).andExpect(jsonPath("$.data.audioAJour").value(false));
        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.segments[0].texte").value("Texte retouché par le formateur"))
                .andExpect(jsonPath("$.data.aAudio").value(false));
        envoyer(get(url(id, "/audio", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());

        ia.blocageAudioExplication.countDown();
        attendreFin(id, "fr", "monologue");
        assertThat(ia.demandesAudioExplication).hasSize(2);
        assertThat(ia.demandesAudioExplication.get(1).segments().get(0).texte()).isEqualTo("Texte retouché par le formateur");
        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.aAudio").value(true));
        envoyer(get(url(id, "/audio", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(status().isOk());
        assertThat(fichiers("audio")).hasSize(1); // l'ancien fichier est supprimé
    }

    // ------------------------------------------------------------------ correction et historique

    @Test
    void correctionRecalculeLaFideliteSansLLM() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");
        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", correction("Un fait inventé par le formateur"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scoreFidelite").value(70))
                .andExpect(jsonPath("$.data.alertes[0]").value("Alerte recalculée"))
                .andExpect(jsonPath("$.data.modifieeParFormateur").value(true))
                .andExpect(jsonPath("$.data.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.segments[0].texte").value("Un fait inventé par le formateur"))
                .andExpect(jsonPath("$.data.segments[0].justifiee").value(false))
                .andExpect(jsonPath("$.data.segments[1].justifiee").value(true)); // seul le segment corrigé est suspect
        // L'IA a reçu le texte corrigé ET le cours complet (pages), pas la fiche.
        var demande = ia.demandesFideliteExplication.get(0);
        assertThat(demande.segments().get(0).texte()).isEqualTo("Un fait inventé par le formateur");
        assertThat(demande.pages()).isNotEmpty();
        assertThat(ia.demandesScript).hasSize(1); // aucun nouveau script : pas de LLM pour recalculer la fidélité
    }

    @Test
    void correctionInvalide() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");
        // Texte vide, segments manquants, en double, inconnus : refusés.
        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", "{\"segments\":[{\"ordre\":1,\"texte\":\" \"}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").exists());
        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", "{\"segments\":[{\"ordre\":1,\"texte\":\"a\"}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("ni en ajouter ni en supprimer")));
        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", "{\"segments\":[]}").andExpect(status().isBadRequest());
        String doublon = "{\"segments\":[" + "{\"ordre\":1,\"texte\":\"a\"},".repeat(6).replaceAll(",$", "") + "]}";
        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", doublon).andExpect(status().isBadRequest());
        envoyer(put(url(id, "", "fr", "dialogue")), FORMATEUR_A, "ENSEIGNANT", correction("x")).andExpect(status().isNotFound());
        assertThat(ia.demandesFideliteExplication).isEmpty();
    }

    @Test
    void corrigerUneVersionValideeEnCreeUneNouvelleEtLApprenantGardeLAncienne() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        ia.texteScript = "Version validée";
        validerComplet(id, "fr", "monologue");

        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", correction("Version corrigée"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.aAudio").value(false)).andExpect(jsonPath("$.data.aPuEcouter").value(false))
                .andExpect(jsonPath("$.data.valideeDisponible").value(true));
        assertThat(explicationRepository.findByCoursId(id)).hasSize(2); // historique conservé

        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.segments[0].texte").value("Bonjour. Version validée"));
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.segments[0].texte").value("Version corrigée"));
        // La nouvelle version n'a pas d'aperçu : elle ne se valide pas sans l'avoir écoutée.
        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isConflict());
        // L'apprenant a toujours son audio.
        envoyer(get(url(id, "/audio", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(status().isOk());
    }

    @Test
    void regenererGardeLHistoriqueEtLApprenantVoitLaDerniereValidee() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        ia.texteScript = "V1";
        validerComplet(id, "fr", "monologue");
        ia.texteScript = "V2";
        scriptGenere(id, "fr", "monologue");

        assertThat(explicationRepository.findByCoursId(id)).hasSize(2);
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.segments[0].texte").value("Bonjour. V2")).andExpect(jsonPath("$.data.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.valideeDisponible").value(true));
        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.segments[0].texte").value("Bonjour. V1"));
    }

    // ------------------------------------------------------------------ anglais : traduction du français validé

    @Test
    void anglaisExigeLeFrancaisValideDuMemeMode() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        generer(FORMATEUR_A, id, "en", "monologue")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("Valide d'abord l'explication en français")));
        scriptGenere(id, "fr", "monologue"); // français généré mais pas validé
        generer(FORMATEUR_A, id, "en", "monologue").andExpect(status().isBadRequest());
        validerComplet(id, "fr", "monologue");
        generer(FORMATEUR_A, id, "en", "dialogue").andExpect(status().isBadRequest()); // le français validé est en monologue
        assertThat(ia.demandesTraduction).isEmpty();
    }

    @Test
    void anglaisTraduitLeScriptFrancaisValideEnGardantLesExtraitsFrancais() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        validerComplet(id, "fr", "monologue");

        generer(FORMATEUR_A, id, "en", "monologue")
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.etapes[1].etape").value("TRADUCTION"));
        attendreFin(id, "en", "monologue");

        var traduction = ia.demandesTraduction.get(0);
        assertThat(traduction.langueCible()).isEqualTo("en");
        assertThat(traduction.langueSource()).isEqualTo("fr");
        assertThat(traduction.segments()).hasSize(6);
        assertThat(traduction.segments().get(1).extraitSource()).isEqualTo("ne garde que les lignes"); // l'extrait FR accompagne le texte
        assertThat(ia.delais).contains("traduction=330000");

        envoyer(get(url(id, "", "en", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.langue").value("en"))
                .andExpect(jsonPath("$.data.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.versionPrompt").value("traduction_v1"))
                .andExpect(jsonPath("$.data.segments[1].texte").value("Première idée. Texte du script [EN]"))
                .andExpect(jsonPath("$.data.segments[1].extraitSource").value("ne garde que les lignes"))
                .andExpect(jsonPath("$.data.segments[1].page").value(1));
        assertThat(explicationRepository.findByCoursId(id).stream().filter(e -> e.getLangue() == LangueExplication.EN).findFirst().orElseThrow()
                .getSourceId()).isNotNull();
        // Invisible de l'apprenant tant qu'elle n'est pas validée (avec son propre aperçu).
        envoyer(get(url(id, "", "en", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data").doesNotExist());
        envoyer(patch(url(id, "/valider", "en", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isConflict());
        validerAvecApercu(id, "en", "monologue");
        envoyer(get(url(id, "", "en", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.langue").value("en"));
        envoyer(get("/api/cours/" + id + "/explication/disponibles"), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void revaliderLeFrancaisRenvoieLAnglaisValideAGeneree() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        validerComplet(id, "fr", "monologue");
        generer(FORMATEUR_A, id, "en", "monologue");
        attendreFin(id, "en", "monologue");
        validerAvecApercu(id, "en", "monologue");
        envoyer(get(url(id, "", "en", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.statut").value("VALIDEE"));

        // Nouveau français, validé à son tour.
        ia.texteScript = "Nouveau français";
        validerComplet(id, "fr", "monologue");

        envoyer(get(url(id, "", "en", "monologue")), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist()); // l'apprenant n'a plus l'anglais
        envoyer(get(url(id, "", "en", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.aRetraduire").value(true))
                .andExpect(jsonPath("$.data.alertes[0]").value(containsString("à refaire")));
        envoyer(get("/api/cours/" + id + "/explication/disponibles"), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].langue").value("fr"));
        // Une nouvelle traduction repart du nouveau français.
        generer(FORMATEUR_A, id, "en", "monologue");
        attendreFin(id, "en", "monologue");
        assertThat(ia.demandesTraduction.get(1).segments().get(0).texte()).isEqualTo("Bonjour. Nouveau français");
    }

    @Test
    void monologueEtDialogueSontDesVersionsIndependantes() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        validerComplet(id, "fr", "monologue");
        validerComplet(id, "fr", "dialogue");
        envoyer(get("/api/cours/" + id + "/explication/disponibles"), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].mode").value(hasItem("dialogue")))
                .andExpect(jsonPath("$.data[*].mode").value(hasItem("monologue")));
        envoyer(get(url(id, "", "fr", "dialogue")), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.mode").value("dialogue")).andExpect(jsonPath("$.data.segments[4].locuteur").value("etudiant"));
        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.segments[4].locuteur").value("prof"));
        // Re-valider le monologue ne touche pas au dialogue.
        validerComplet(id, "fr", "monologue");
        envoyer(get(url(id, "", "fr", "dialogue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.statut").value("VALIDEE"));
    }

    // ------------------------------------------------------------------ suivi, concurrence, erreurs

    @Test
    void unSeulTraitementParCombinaisonMaisPlusieursCombinaisonsEnParallele() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        CountDownLatch verrou = new CountDownLatch(1);
        ia.blocageScript = verrou;

        generer(FORMATEUR_A, id, "fr", "monologue").andExpect(status().isAccepted());
        generer(FORMATEUR_A, id, "fr", "monologue").andExpect(status().isConflict());
        envoyer(get(url(id, "/traitement", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.enCours").value(true));
        generer(FORMATEUR_A, id, "fr", "dialogue").andExpect(status().isAccepted()); // un autre mode : pas de conflit
        // Le suivi d'une explication n'est pas celui de la fiche.
        envoyer(get("/api/cours/" + id + "/traitement"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.etapes.length()").value(0));

        verrou.countDown();
        attendreFin(id, "fr", "monologue");
        attendreFin(id, "fr", "dialogue");
        generer(FORMATEUR_A, id, "fr", "monologue").andExpect(status().isAccepted());
        attendreFin(id, "fr", "monologue");
    }

    @Test
    void echecDuScriptAnnuleLaSuiteEtLeMessageEstLisible() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        ia.panneScript = new ServiceUnavailableException("Le quota de l'IA est atteint, réessaie dans quelques minutes.");
        generer(FORMATEUR_A, id, "fr", "monologue").andExpect(status().isAccepted());
        attendreFin(id, "fr", "monologue");
        envoyer(get(url(id, "/traitement", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.enCours").value(false))
                .andExpect(jsonPath("$.data.etapes[0].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[1].statut").value("ECHEC"))
                .andExpect(jsonPath("$.data.etapes[1].erreur").value(containsString("quota")));
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data").doesNotExist());

        ia.panneScript = new IllegalStateException("détail technique secret");
        generer(FORMATEUR_A, id, "fr", "monologue").andExpect(status().isAccepted());
        attendreFin(id, "fr", "monologue");
        envoyer(get(url(id, "/traitement", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[1].statut").value("ECHEC"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void echecDeLAudioLaisseLeScriptEtEmpecheLaValidation() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");
        ia.panneAudioExplication = new ServiceUnavailableException("La synthèse vocale est indisponible pour le moment.");
        apercu(FORMATEUR_A, id, "fr", "monologue").andExpect(status().isAccepted());
        attendreFin(id, "fr", "monologue");
        envoyer(get(url(id, "/traitement", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[0].etape").value("AUDIO")).andExpect(jsonPath("$.data.etapes[0].statut").value("ECHEC"))
                .andExpect(jsonPath("$.data.etapes[0].erreur").value(containsString("synthèse")));
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.aPuEcouter").value(false)).andExpect(jsonPath("$.data.segments.length()").value(6));
        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isConflict());
        // Une fois la panne passée, le formateur peut relancer l'aperçu.
        ia.panneAudioExplication = null;
        apercu(FORMATEUR_A, id, "fr", "monologue").andExpect(status().isAccepted());
        attendreFin(id, "fr", "monologue");
        envoyer(patch(url(id, "/valider", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
    }

    @Test
    void correctionImpossibleQuandLIAEstIndisponibleRenvoie503() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        scriptGenere(id, "fr", "monologue");
        ia.panneFideliteExplication = new ServiceUnavailableException("Le service d'IA est injoignable pour le moment.");
        envoyer(put(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", correction("Texte du formateur"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(containsString("injoignable")));
        envoyer(get(url(id, "", "fr", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.segments[0].texte").value("Bonjour. Texte du script")); // rien n'a été enregistré
    }

    @Test
    void echecDeLaTraductionLaisseLeFrancaisIntact() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        validerComplet(id, "fr", "monologue");
        ia.panneTraduction = new ServiceUnavailableException("La traduction ne respecte pas le découpage du script.");
        generer(FORMATEUR_A, id, "en", "monologue").andExpect(status().isAccepted());
        attendreFin(id, "en", "monologue");
        envoyer(get(url(id, "/traitement", "en", "monologue")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[1].etape").value("TRADUCTION")).andExpect(jsonPath("$.data.etapes[1].statut").value("ECHEC"));
        envoyer(get(url(id, "", "fr", "monologue")), APPRENANT, "ETUDIANT", null).andExpect(jsonPath("$.data.statut").value("VALIDEE"));
    }

    // ------------------------------------------------------------------ nettoyage

    @Test
    void supprimerUnCoursSupprimeSesExplicationsEtSesAudios() throws Exception {
        long id = coursPublie(FORMATEUR_A);
        validerComplet(id, "fr", "monologue");
        validerComplet(id, "fr", "dialogue");
        assertThat(fichiers("audio")).hasSize(2);

        envoyer(delete("/api/cours/" + id), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());

        assertThat(fichiers("audio")).isEmpty();
        assertThat(explicationRepository.findByCoursId(id)).isEmpty();
        assertThat(traitementRepository.findByCoursId(id)).isEmpty();
    }

    // ------------------------------------------------------------------ aides

    private static String url(long coursId, String route, String langue, String mode) {
        return "/api/cours/" + coursId + "/explication" + route + "?langue=" + langue + "&mode=" + mode;
    }

    private static String correction(String texte) {
        StringBuilder sb = new StringBuilder("{\"segments\":[");
        for (int i = 1; i <= 6; i++) {
            sb.append(i > 1 ? "," : "").append("{\"ordre\":").append(i).append(",\"texte\":\"").append(i == 1 ? texte : "Segment " + i)
                    .append("\",\"extraitSource\":\"").append(i == 2 || i == 4 ? "ne garde que les lignes" : "").append("\"}");
        }
        return sb.append("]}").toString();
    }

    private ResultActions generer(long utilisateur, long coursId, String langue, String mode) throws Exception {
        return envoyer(post(url(coursId, "/generer", langue, mode)), utilisateur, "ENSEIGNANT", null);
    }

    private ResultActions apercu(long utilisateur, long coursId, String langue, String mode) throws Exception {
        return envoyer(post(url(coursId, "/apercu", langue, mode)), utilisateur, "ENSEIGNANT", null);
    }

    /** Génère le script (français) et attend la fin du traitement. */
    private void scriptGenere(long id, String langue, String mode) throws Exception {
        generer(FORMATEUR_A, id, langue, mode).andExpect(status().isAccepted());
        attendreFin(id, langue, mode);
    }

    private void validerAvecApercu(long id, String langue, String mode) throws Exception {
        apercu(FORMATEUR_A, id, langue, mode).andExpect(status().isAccepted());
        attendreFin(id, langue, mode);
        envoyer(patch(url(id, "/valider", langue, mode)), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        attendreFin(id, langue, mode);
    }

    /** Français : génère, écoute un aperçu, valide. */
    private void validerComplet(long id, String langue, String mode) throws Exception {
        scriptGenere(id, langue, mode);
        validerAvecApercu(id, langue, mode);
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

    /** Cours publié (visible des apprenants) dont l'unique chapitre contient assez de texte pour être expliqué. */
    private long coursPublie(long formateur) throws Exception {
        long id = creerCours(formateur, "Jointures SQL");
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

    /** Attend la fin du dernier traitement de cette combinaison (au plus 15 s) : il s'exécute dans un autre thread. */
    private void attendreFin(long coursId, String langue, String mode) throws Exception {
        String portee = TraitementIA.porteeExplication(LangueExplication.depuis(langue), ModeExplication.depuis(mode));
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            var dernier = traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, portee);
            if (dernier.isPresent()) {
                var lignes = traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.get().getExecution());
                if (lignes.stream().noneMatch(l -> l.getStatut().actif())) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Le traitement IA n'est pas terminé après 15 s");
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
            return Files.createTempDirectory("bawsla-cours-explication-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

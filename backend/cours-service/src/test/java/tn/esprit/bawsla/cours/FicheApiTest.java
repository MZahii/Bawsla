package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import tn.esprit.bawsla.cours.client.AudioIaRequestDto;
import tn.esprit.bawsla.cours.client.FicheIaDto;
import tn.esprit.bawsla.cours.client.FicheIaRequestDto;
import tn.esprit.bawsla.cours.client.FideliteIaDto;
import tn.esprit.bawsla.cours.client.FideliteIaDto.NotionJustifieeDto;
import tn.esprit.bawsla.cours.client.FideliteIaRequestDto;
import tn.esprit.bawsla.cours.client.IaClient;
import tn.esprit.bawsla.cours.client.NotionIaDto;
import tn.esprit.bawsla.cours.common.ApiResponse;
import tn.esprit.bawsla.cours.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.repository.AudioRevisionRepository;
import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.FicheRevisionRepository;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;

/**
 * Fiche de révision générée par l'IA : lancement asynchrone et suivi, visibilité selon le statut, historique,
 * correction avec recalcul de fidélité, validation, audio, erreurs de l'IA et nettoyage.
 * L'IA est remplacée par {@link FauxIa} (aucun appel réseau).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cours_fiche;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FicheApiTest.IaDeTest.class)
class FicheApiTest {

    private static final Path STOCKAGE = dossierTemporaire();

    private static final long FORMATEUR_A = 10L;
    private static final long FORMATEUR_B = 20L;
    private static final long APPRENANT = 30L;
    private static final long ADMIN = 1L;

    private static final String JETON = "Bearer jeton-de-test";
    private static final String CONTENU = "Une jointure interne ne garde que les lignes qui ont une correspondance dans les deux tables. "
            + "Contactez le formateur à prof@exemple.tn pour toute question.";
    private static final byte[] MP3 = FauxIa.MP3;

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
    @Autowired TraitementIARepository traitementRepository;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("cours.stockage.dossier", STOCKAGE::toString);
    }

    @BeforeEach
    void nettoyer() throws IOException {
        ia.reinitialiser();
        traitementRepository.deleteAll();
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

    // ------------------------------------------------------------------ génération et suivi

    @Test
    void generationEnArrierePlanAvecSuiviDesEtapes() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);

        generer(FORMATEUR_A, id)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.execution").isNotEmpty())
                .andExpect(jsonPath("$.data.etapes.length()").value(3))
                .andExpect(jsonPath("$.data.etapes[0].etape").value("TEXTE"))
                .andExpect(jsonPath("$.data.etapes[2].etape").value("AUDIO"));
        attendreFinDuTraitement(id);

        envoyer(get("/api/cours/" + id + "/traitement"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.enCours").value(false))
                .andExpect(jsonPath("$.data.etapes[0].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[1].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[1].dureeMs").isNumber())
                .andExpect(jsonPath("$.data.etapes[2].statut").value("TERMINE"));

        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.resume").value("Résumé généré"))
                .andExpect(jsonPath("$.data.motsCles.length()").value(2))
                .andExpect(jsonPath("$.data.notions.length()").value(2))
                .andExpect(jsonPath("$.data.notions[0].extraitSource").value("ne garde que les lignes"))
                .andExpect(jsonPath("$.data.notions[0].justifiee").value(true))
                .andExpect(jsonPath("$.data.notions[1].justifiee").value(false))
                .andExpect(jsonPath("$.data.scoreFidelite").value(90))
                .andExpect(jsonPath("$.data.alertes.length()").value(1))
                .andExpect(jsonPath("$.data.versionPrompt").value("v3"))
                .andExpect(jsonPath("$.data.modele").value("gemini-test"))
                .andExpect(jsonPath("$.data.aAudio").value(true))
                .andExpect(jsonPath("$.data.fournisseurAudio").value("elevenlabs"))
                .andExpect(jsonPath("$.data.valideeDisponible").value(false))
                .andExpect(jsonPath("$.data.modifieeParFormateur").value(false));

        // L'IA reçoit le Authorization de l'utilisateur (la gateway revalide), le niveau du cours et le contenu des chapitres.
        assertThat(ia.autorisations).containsOnly(JETON);
        FicheIaRequestDto demande = ia.demandesFiche.get(0);
        assertThat(demande.niveau()).isEqualTo(Niveau.DEBUTANT);
        assertThat(demande.coursId()).isEqualTo(id);
        assertThat(demande.texte()).contains("Chapitre 1 : Introduction").contains("jointure interne");
        assertThat(ia.demandesAudio.get(0).texte()).isEqualTo("Résumé généré");
        assertThat(ia.demandesAudio.get(0).langue()).isEqualTo("fr");
    }

    @Test
    void lePdfEstLaSourcePrivilegieeEtSesDonneesPersonnellesSontMasquees() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        envoyerPdf(FORMATEUR_A, id);

        generer(FORMATEUR_A, id).andExpect(status().isAccepted());
        attendreFinDuTraitement(id);

        String texte = ia.demandesFiche.get(0).texte();
        assertThat(texte).contains("Introduction a Spring Boot").doesNotContain("Chapitre 1");
        assertThat(texte).doesNotContain("prof@example.com").doesNotContain("22 123 456").contains("[EMAIL]").contains("[TELEPHONE]");
    }

    @Test
    void lesDonneesPersonnellesDesChapitresSontMasqueesAvantLIA() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        generer(FORMATEUR_A, id).andExpect(status().isAccepted());
        attendreFinDuTraitement(id);
        assertThat(ia.demandesFiche.get(0).texte()).doesNotContain("prof@exemple.tn").contains("[EMAIL]");
    }

    @Test
    void sansPdfNiContenuLaGenerationEstRefusee() throws Exception {
        long id = creerCours(FORMATEUR_A, "Vide");
        creerChapitre(FORMATEUR_A, id, "Intro", "court");
        generer(FORMATEUR_A, id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("PDF")));
        assertThat(traitementRepository.count()).isZero();
        assertThat(ia.demandesFiche).isEmpty();
    }

    @Test
    void unSeulTraitementACLaFois() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        CountDownLatch verrou = new CountDownLatch(1);
        ia.blocageFiche = verrou;

        generer(FORMATEUR_A, id).andExpect(status().isAccepted());
        generer(FORMATEUR_A, id).andExpect(status().isConflict());
        envoyer(get("/api/cours/" + id + "/traitement"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.enCours").value(true));

        verrou.countDown();
        attendreFinDuTraitement(id);
        generer(FORMATEUR_A, id).andExpect(status().isAccepted());
        attendreFinDuTraitement(id);
    }

    // ------------------------------------------------------------------ droits

    @Test
    void seulLeProprietaireOuLAdminGenereEtSuit() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        generer(FORMATEUR_B, id).andExpect(status().isForbidden());
        generer(APPRENANT, id, "ETUDIANT").andExpect(status().isForbidden());
        envoyer(get("/api/cours/" + id + "/traitement"), FORMATEUR_B, "ENSEIGNANT", null).andExpect(status().isForbidden());
        envoyer(get("/api/cours/" + id + "/traitement"), APPRENANT, "ETUDIANT", null).andExpect(status().isForbidden());
        assertThat(ia.demandesFiche).isEmpty();

        generer(ADMIN, id, "ADMIN").andExpect(status().isAccepted());
        attendreFinDuTraitement(id);
        envoyer(put("/api/cours/" + id + "/fiche"), FORMATEUR_B, "ENSEIGNANT", fichePut("Piraté", "n")).andExpect(status().isForbidden());
        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_B, "ENSEIGNANT", null).andExpect(status().isForbidden());
        envoyer(patch("/api/cours/" + id + "/fiche/valider"), APPRENANT, "ETUDIANT", null).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ validation et visibilité

    @Test
    void l_apprenant_ne_voit_la_fiche_et_l_audio_qu_apres_validation() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);

        // Pas encore de fiche validée : 200 sans data (pas une erreur). L'audio, lui, reste introuvable.
        envoyer(get("/api/cours/" + id + "/fiche"), APPRENANT, "ETUDIANT", null).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        envoyer(get("/api/cours/" + id + "/audio"), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_B, "ENSEIGNANT", null).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.data.dateValidation").isNotEmpty());

        // L'apprenant voit le contenu, mais ni le score, ni les alertes, ni les notions suspectes, ni le modèle.
        envoyer(get("/api/cours/" + id + "/fiche"), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resume").value("Résumé généré"))
                .andExpect(jsonPath("$.data.notions.length()").value(2))
                .andExpect(jsonPath("$.data.aAudio").value(true))
                .andExpect(jsonPath("$.data.scoreFidelite").doesNotExist())
                .andExpect(jsonPath("$.data.alertes").doesNotExist())
                .andExpect(jsonPath("$.data.modele").doesNotExist())
                .andExpect(jsonPath("$.data.versionPrompt").doesNotExist())
                .andExpect(jsonPath("$.data.notions[0].justifiee").doesNotExist());
        byte[] audio = envoyer(get("/api/cours/" + id + "/audio"), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk()).andExpect(content().contentType("audio/mpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(audio).isEqualTo(MP3);
        // Le formateur voit toujours tout.
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.scoreFidelite").value(90)).andExpect(jsonPath("$.data.valideeDisponible").value(true));
    }

    @Test
    void lAudioWavDeGeminiEstStockeEtServiAvecSonVraiTypeEtSonFournisseur() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        ia.mimeAudio = "audio/wav";
        ia.fournisseurAudio = "gemini";
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);
        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());

        assertThat(fichiers("audio")).hasSize(1);
        assertThat(fichiers("audio").get(0).getFileName().toString()).endsWith(".wav");
        // L'apprenant voit quel fournisseur a généré la voix, et reçoit l'audio avec son vrai type.
        envoyer(get("/api/cours/" + id + "/fiche"), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.aAudio").value(true))
                .andExpect(jsonPath("$.data.fournisseurAudio").value("gemini"));
        envoyer(get("/api/cours/" + id + "/audio"), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk()).andExpect(content().contentType("audio/wav"));
    }

    @Test
    void sansEnTeteFournisseurOnGardeUnAudioSansMention() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        ia.fournisseurAudio = "";
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.aAudio").value(true))
                .andExpect(jsonPath("$.data.fournisseurAudio").doesNotExist());
    }

    @Test
    void coursIntrouvableOuNonVisibleResteUn404MemePourLaFiche() throws Exception {
        long brouillon = creerCours(FORMATEUR_A, "Brouillon sans fiche");
        envoyer(get("/api/cours/" + brouillon + "/fiche"), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(get("/api/cours/999999/fiche"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isNotFound());
    }

    @Test
    void validerSansFicheEtLireSansFiche() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").doesNotExist());
        envoyer(get("/api/cours/" + id + "/traitement"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.etapes.length()").value(0));
    }

    @Test
    void regenererGardeLHistoriqueEtLApprenantVoitLaDerniereFicheValidee() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        ia.resume = "Version 1";
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);
        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());

        ia.resume = "Version 2";
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);

        assertThat(ficheRepository.findByCoursId(id)).hasSize(2); // l'historique est conservé
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.resume").value("Version 2"))
                .andExpect(jsonPath("$.data.statut").value("GENEREE"))
                .andExpect(jsonPath("$.data.valideeDisponible").value(true));
        envoyer(get("/api/cours/" + id + "/fiche"), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.resume").value("Version 1"));

        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        envoyer(get("/api/cours/" + id + "/fiche"), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.resume").value("Version 2"));
    }

    // ------------------------------------------------------------------ correction

    @Test
    void correctionRecalculeLaFideliteSansLLM() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);

        String corps = "{\"resume\":\"Résumé corrigé\",\"motsCles\":[\"SQL\",\"sql\",\" jointure \"],"
                + "\"notions\":[{\"titre\":\"INNER JOIN\",\"definition\":\"Définition corrigée\",\"extraitSource\":\"ne garde que les lignes\"},"
                + "{\"titre\":\"Notion suspecte\",\"definition\":\"Ajoutée\",\"extraitSource\":\"phrase du formateur\"}]}";
        envoyer(put("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", corps)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resume").value("Résumé corrigé"))
                .andExpect(jsonPath("$.data.motsCles.length()").value(2)) // doublons retirés
                .andExpect(jsonPath("$.data.scoreFidelite").value(77))
                .andExpect(jsonPath("$.data.alertes[0]").value("Alerte recalculée"))
                .andExpect(jsonPath("$.data.notions[0].justifiee").value(true))
                .andExpect(jsonPath("$.data.notions[1].justifiee").value(false))
                .andExpect(jsonPath("$.data.modifieeParFormateur").value(true))
                .andExpect(jsonPath("$.data.statut").value("GENEREE"))
                // Le résumé a changé : l'ancien audio ne correspond plus.
                .andExpect(jsonPath("$.data.aAudio").value(false));
        assertThat(ficheRepository.findByCoursId(id)).hasSize(1); // modifiée sur place, tant qu'elle n'est pas validée
        assertThat(ia.autorisations).containsOnly(JETON);
    }

    @Test
    void corrigerUneFicheValideeCreeUneNouvelleFicheEtLesApprenantsGardentLAncienne() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        ia.resume = "Version validée";
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);
        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());

        envoyer(put("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", fichePut("Version corrigée", "Notion"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("GENEREE"));

        assertThat(ficheRepository.findByCoursId(id)).hasSize(2);
        envoyer(get("/api/cours/" + id + "/fiche"), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.resume").value("Version validée"));
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.resume").value("Version corrigée"));

        // À la validation, l'audio du résumé corrigé est produit en arrière-plan.
        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        attendreFinDuTraitement(id);
        assertThat(ia.demandesAudio.get(ia.demandesAudio.size() - 1).texte()).isEqualTo("Version corrigée");
        envoyer(get("/api/cours/" + id + "/fiche"), APPRENANT, "ETUDIANT", null)
                .andExpect(jsonPath("$.data.resume").value("Version corrigée")).andExpect(jsonPath("$.data.aAudio").value(true));
    }

    @Test
    void donneesDeCorrectionInvalides() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);
        envoyer(put("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", "{\"resume\":\" \",\"motsCles\":[],\"notions\":[]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.resume").exists());
        envoyer(put("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT",
                "{\"resume\":\"ok\",\"motsCles\":[],\"notions\":[{\"titre\":\"\",\"definition\":\"d\"}]}")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ erreurs de l'IA

    @Test
    void echecDeLaFicheAnnuleLAudioEtLeMessageEstLisible() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        ia.panneFiche = new ServiceUnavailableException("Le quota de l'IA est atteint, réessaie dans quelques minutes.");

        generer(FORMATEUR_A, id).andExpect(status().isAccepted());
        attendreFinDuTraitement(id);

        envoyer(get("/api/cours/" + id + "/traitement"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[0].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[1].statut").value("ECHEC"))
                .andExpect(jsonPath("$.data.etapes[1].erreur").value(containsString("quota")))
                .andExpect(jsonPath("$.data.etapes[2].statut").value("ANNULE"));
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        assertThat(ia.demandesAudio).isEmpty();
    }

    @Test
    void uneErreurInattendueNeFuitePasSesDetails() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        ia.panneFiche = new IllegalStateException("détail technique secret");
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);
        envoyer(get("/api/cours/" + id + "/traitement"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[1].statut").value("ECHEC"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void echecDeLAudioGardeLaFiche() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        ia.panneAudio = new ServiceUnavailableException("La synthèse vocale est indisponible pour le moment");

        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);

        envoyer(get("/api/cours/" + id + "/traitement"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[1].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[2].statut").value("ECHEC"))
                .andExpect(jsonPath("$.data.etapes[2].erreur").value(containsString("synthèse")));
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.aAudio").value(false));
        envoyer(get("/api/cours/" + id + "/audio"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isNotFound());
    }

    @Test
    void correctionImpossibleQuandLIAEstIndisponibleRenvoie503() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);
        ia.panneFidelite = new ServiceUnavailableException("Le service d'IA est injoignable pour le moment.");

        envoyer(put("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", fichePut("Texte du formateur", "Notion"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("injoignable")));
        envoyer(get("/api/cours/" + id + "/fiche"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.resume").value("Résumé généré")); // rien n'a été enregistré
    }

    // ------------------------------------------------------------------ nettoyage

    @Test
    void supprimerUnCoursSupprimeFichesAudiosEtSuivi() throws Exception {
        long id = coursPublieAvecContenu(FORMATEUR_A);
        generer(FORMATEUR_A, id);
        attendreFinDuTraitement(id);
        envoyer(patch("/api/cours/" + id + "/fiche/valider"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        assertThat(fichiers("audio")).hasSize(1);

        envoyer(delete("/api/cours/" + id), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());

        assertThat(fichiers("audio")).isEmpty();
        assertThat(ficheRepository.findByCoursId(id)).isEmpty();
        assertThat(audioRepository.findByCoursId(id)).isEmpty();
        assertThat(traitementRepository.findByCoursId(id)).isEmpty();
    }

    // ------------------------------------------------------------------ aides

    private ResultActions generer(long utilisateur, long coursId) throws Exception {
        return generer(utilisateur, coursId, "ENSEIGNANT");
    }

    private ResultActions generer(long utilisateur, long coursId, String role) throws Exception {
        return envoyer(post("/api/cours/" + coursId + "/fiche/generer"), utilisateur, role, null);
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder requete, long id, String role, String corps) throws Exception {
        requete.header("X-User-Id", id).header("X-User-Role", role).header("Authorization", JETON);
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mvc.perform(requete);
    }

    private static String fichePut(String resume, String titreNotion) {
        return "{\"resume\":\"" + resume + "\",\"motsCles\":[\"SQL\"],\"notions\":[{\"titre\":\"" + titreNotion
                + "\",\"definition\":\"Définition\",\"extraitSource\":\"extrait\"}]}";
    }

    private long creerCours(long formateur, String titre) throws Exception {
        String corps = "{\"titre\":\"" + titre + "\",\"categorie\":\"Données\",\"niveau\":\"DEBUTANT\"}";
        return identifiant(envoyer(post("/api/cours"), formateur, "ENSEIGNANT", corps).andExpect(status().isCreated()));
    }

    private long creerChapitre(long formateur, long coursId, String titre, String contenu) throws Exception {
        String corps = "{\"titre\":\"" + titre + "\",\"contenu\":\"" + contenu + "\",\"dureeMinutes\":15}";
        return identifiant(envoyer(post("/api/cours/" + coursId + "/chapitres"), formateur, "ENSEIGNANT", corps)
                .andExpect(status().isCreated()));
    }

    /** Cours publié dont l'unique chapitre contient du texte (assez long pour servir de source à l'IA). */
    private long coursPublieAvecContenu(long formateur) throws Exception {
        long id = creerCours(formateur, "Jointures SQL");
        creerChapitre(formateur, id, "Introduction", CONTENU);
        envoyer(patch("/api/cours/" + id + "/statut"), formateur, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}").andExpect(status().isOk());
        return id;
    }

    private void envoyerPdf(long formateur, long coursId) throws Exception {
        byte[] octets = new ClassPathResource("exemple.pdf").getContentAsByteArray();
        mvc.perform(multipart("/api/cours/" + coursId + "/pdf").file(new MockMultipartFile("fichier", "exemple.pdf", "application/pdf", octets))
                .header("X-User-Id", formateur).header("X-User-Role", "ENSEIGNANT")).andExpect(status().isOk());
    }

    private static long identifiant(ResultActions actions) throws Exception {
        Number n = JsonPath.read(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
        return n.longValue();
    }

    /** Attend la fin du traitement lancé (au plus 15 s) : il s'exécute dans un autre thread. */
    private void attendreFinDuTraitement(long coursId) throws Exception {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            var dernier = traitementRepository.findFirstByCoursIdOrderByIdDesc(coursId);
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
            return Files.createTempDirectory("bawsla-cours-fiche-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

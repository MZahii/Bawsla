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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;

/**
 * Tests d'API du module Cours, avec les en-têtes X-User-* que poserait la gateway :
 * droits, visibilité selon le statut, publication, chapitres, fichiers et extraction du texte du PDF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoursApiTest {

    private static final Path STOCKAGE = creerDossierTemporaire();

    // Utilisateurs fictifs : deux formateurs, un apprenant, un admin.
    private static final long FORMATEUR_A = 10L;
    private static final long FORMATEUR_B = 20L;
    private static final long APPRENANT = 30L;
    private static final long ADMIN = 1L;

    private static final String CHAPITRE = "{\"titre\":\"Introduction\",\"contenu\":\"Bienvenue\",\"dureeMinutes\":20}";

    @Autowired MockMvc mvc;
    @Autowired CoursRepository coursRepository;
    @Autowired PageDocumentRepository pageRepository;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("cours.stockage.dossier", STOCKAGE::toString);
    }

    @BeforeEach
    void nettoyer() throws IOException {
        pageRepository.deleteAll();
        coursRepository.deleteAll();
        if (Files.exists(STOCKAGE)) {
            try (Stream<Path> chemins = Files.walk(STOCKAGE)) {
                chemins.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(STOCKAGE)).forEach(p -> p.toFile().delete());
            }
        }
    }

    // ------------------------------------------------------------------ droits

    @Test
    void apprenantNePeutPasCreerUnCours() throws Exception {
        envoyer(post("/api/cours"), APPRENANT, "ETUDIANT", json("Cours interdit")).andExpect(status().isForbidden());
    }

    @Test
    void autreFormateurNePeutRienModifierSurLeCoursDunAutre() throws Exception {
        long id = creerCours(FORMATEUR_A, "Cours de A");
        long chapitre = creerChapitre(FORMATEUR_A, id);

        envoyer(put("/api/cours/" + id), FORMATEUR_B, "ENSEIGNANT", json("Piraté")).andExpect(status().isForbidden());
        envoyer(delete("/api/cours/" + id), FORMATEUR_B, "ENSEIGNANT", null).andExpect(status().isForbidden());
        envoyer(patch("/api/cours/" + id + "/statut"), FORMATEUR_B, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}")
                .andExpect(status().isForbidden());
        envoyer(post("/api/cours/" + id + "/chapitres"), FORMATEUR_B, "ENSEIGNANT", CHAPITRE).andExpect(status().isForbidden());
        envoyer(put("/api/cours/" + id + "/chapitres/" + chapitre), FORMATEUR_B, "ENSEIGNANT", CHAPITRE)
                .andExpect(status().isForbidden());
        envoyer(delete("/api/cours/" + id + "/chapitres/" + chapitre), FORMATEUR_B, "ENSEIGNANT", null)
                .andExpect(status().isForbidden());
        envoyer(patch("/api/cours/" + id + "/chapitres/ordre"), FORMATEUR_B, "ENSEIGNANT", "{\"ids\":[" + chapitre + "]}")
                .andExpect(status().isForbidden());
        envoyerFichier("pdf", FORMATEUR_B, "ENSEIGNANT", id, pdfDeTest("exemple.pdf")).andExpect(status().isForbidden());
        envoyerFichier("image", FORMATEUR_B, "ENSEIGNANT", id, imageDeTest()).andExpect(status().isForbidden());

        // Le cours de A n'a pas bougé.
        lire(FORMATEUR_A, "ENSEIGNANT", id).andExpect(jsonPath("$.data.titre").value("Cours de A"));
    }

    @Test
    void apprenantNePeutPasModifierNiSupprimer() throws Exception {
        long id = creerCoursPublie(FORMATEUR_A, "Cours publié");
        envoyer(put("/api/cours/" + id), APPRENANT, "ETUDIANT", json("Piraté")).andExpect(status().isForbidden());
        envoyer(delete("/api/cours/" + id), APPRENANT, "ETUDIANT", null).andExpect(status().isForbidden());
        envoyer(patch("/api/cours/" + id + "/statut"), APPRENANT, "ETUDIANT", "{\"statut\":\"ARCHIVE\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void proprietaireEtAdminPeuventModifier() throws Exception {
        long id = creerCours(FORMATEUR_A, "Avant");
        envoyer(put("/api/cours/" + id), FORMATEUR_A, "ENSEIGNANT", json("Après proprio"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.titre").value("Après proprio"));
        envoyer(put("/api/cours/" + id), ADMIN, "ADMIN", json("Après admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.titre").value("Après admin"));
        envoyer(delete("/api/cours/" + id), ADMIN, "ADMIN", null).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ visibilité

    @Test
    void apprenantNeVoitQueLesCoursPublies() throws Exception {
        long publie = creerCoursPublie(FORMATEUR_A, "Publié");
        long brouillon = creerCours(FORMATEUR_A, "Brouillon");

        envoyer(get("/api/cours"), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].titre").value("Publié"));
        lire(APPRENANT, "ETUDIANT", publie).andExpect(status().isOk());
        lire(APPRENANT, "ETUDIANT", brouillon).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + brouillon + "/chapitres"), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + brouillon + "/texte"), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
    }

    @Test
    void formateurVoitLesCoursPubliesEtTousLesSiens() throws Exception {
        creerCoursPublie(FORMATEUR_A, "Publié de A");
        creerCours(FORMATEUR_A, "Brouillon de A");
        creerCours(FORMATEUR_B, "Brouillon de B");

        envoyer(get("/api/cours"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(content().string(containsString("Publié de A")))
                .andExpect(content().string(containsString("Brouillon de A")))
                .andExpect(content().string(not(containsString("Brouillon de B"))));
        envoyer(get("/api/cours"), FORMATEUR_B, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(content().string(containsString("Publié de A")))
                .andExpect(content().string(containsString("Brouillon de B")))
                .andExpect(content().string(not(containsString("Brouillon de A"))));
    }

    @Test
    void adminVoitTout() throws Exception {
        creerCoursPublie(FORMATEUR_A, "Publié");
        long brouillon = creerCours(FORMATEUR_B, "Brouillon");
        envoyer(get("/api/cours"), ADMIN, "ADMIN", null).andExpect(jsonPath("$.data.length()").value(2));
        lire(ADMIN, "ADMIN", brouillon).andExpect(status().isOk());
    }

    @Test
    void cours_archive_disparait_pour_les_apprenants() throws Exception {
        long id = creerCoursPublie(FORMATEUR_A, "À archiver");
        envoyer(patch("/api/cours/" + id + "/statut"), FORMATEUR_A, "ENSEIGNANT", "{\"statut\":\"ARCHIVE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("ARCHIVE"));
        lire(APPRENANT, "ETUDIANT", id).andExpect(status().isNotFound());
        lire(FORMATEUR_A, "ENSEIGNANT", id).andExpect(status().isOk());
    }

    @Test
    void contratDuCoursGardeSesChampsEtAjouteLesNouveaux() throws Exception {
        long id = creerCoursPublie(FORMATEUR_A, "Contrat");
        lire(APPRENANT, "ETUDIANT", id)
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.titre").value("Contrat"))
                .andExpect(jsonPath("$.data.categorie").value("Backend"))
                .andExpect(jsonPath("$.data.niveau").value("DEBUTANT"))
                .andExpect(jsonPath("$.data.enseignantId").value(FORMATEUR_A))
                .andExpect(jsonPath("$.data.statut").value("PUBLIE"))
                .andExpect(jsonPath("$.data.prix").value(0.0))
                .andExpect(jsonPath("$.data.nbChapitres").value(1))
                .andExpect(jsonPath("$.data.dureeTotaleMinutes").value(20))
                .andExpect(jsonPath("$.data.aPdf").value(false));
    }

    // ------------------------------------------------------------------ publication

    @Test
    void publicationRefuseeSansChapitre() throws Exception {
        long id = creerCours(FORMATEUR_A, "Vide");
        envoyer(patch("/api/cours/" + id + "/statut"), FORMATEUR_A, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("au moins un chapitre")));
        lire(FORMATEUR_A, "ENSEIGNANT", id).andExpect(jsonPath("$.data.statut").value("BROUILLON"));

        creerChapitre(FORMATEUR_A, id);
        envoyer(patch("/api/cours/" + id + "/statut"), FORMATEUR_A, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statut").value("PUBLIE"));
    }

    @Test
    void prixEnregistreEtConserveQuandAbsent() throws Exception {
        String avecPrix = "{\"titre\":\"Payant\",\"categorie\":\"Backend\",\"niveau\":\"AVANCE\",\"prix\":49}";
        MvcResultId cree = new MvcResultId(envoyer(post("/api/cours"), FORMATEUR_A, "ENSEIGNANT", avecPrix)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.prix").value(49.0)));
        envoyer(put("/api/cours/" + cree.id), FORMATEUR_A, "ENSEIGNANT", "{\"titre\":\"Payant bis\"}")
                .andExpect(jsonPath("$.data.prix").value(49.0));
        envoyer(post("/api/cours"), FORMATEUR_A, "ENSEIGNANT", "{\"titre\":\"Négatif\",\"prix\":-5}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.prix").exists());
    }

    // ------------------------------------------------------------------ chapitres

    @Test
    void chapitresSontOrdonnesEtReordonnables() throws Exception {
        long id = creerCours(FORMATEUR_A, "Plan");
        long un = creerChapitre(FORMATEUR_A, id, "Un");
        long deux = creerChapitre(FORMATEUR_A, id, "Deux");
        long trois = creerChapitre(FORMATEUR_A, id, "Trois");

        envoyer(get("/api/cours/" + id + "/chapitres"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data[0].titre").value("Un"))
                .andExpect(jsonPath("$.data[2].ordre").value(3));

        envoyer(patch("/api/cours/" + id + "/chapitres/ordre"), FORMATEUR_A, "ENSEIGNANT",
                "{\"ids\":[" + trois + "," + un + "," + deux + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].titre").value("Trois"));
        envoyer(get("/api/cours/" + id + "/chapitres"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data[0].titre").value("Trois"))
                .andExpect(jsonPath("$.data[1].titre").value("Un"))
                .andExpect(jsonPath("$.data[2].titre").value("Deux"));

        // Liste incomplète ou avec doublon : refusée.
        envoyer(patch("/api/cours/" + id + "/chapitres/ordre"), FORMATEUR_A, "ENSEIGNANT", "{\"ids\":[" + un + "," + deux + "]}")
                .andExpect(status().isBadRequest());
        envoyer(patch("/api/cours/" + id + "/chapitres/ordre"), FORMATEUR_A, "ENSEIGNANT",
                "{\"ids\":[" + un + "," + un + "," + deux + "]}").andExpect(status().isBadRequest());

        // La suppression renumérote.
        envoyer(delete("/api/cours/" + id + "/chapitres/" + trois), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        envoyer(get("/api/cours/" + id + "/chapitres"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].ordre").value(1))
                .andExpect(jsonPath("$.data[1].ordre").value(2));
    }

    @Test
    void chapitreModifiable() throws Exception {
        long id = creerCours(FORMATEUR_A, "Édition");
        long chapitre = creerChapitre(FORMATEUR_A, id);
        envoyer(put("/api/cours/" + id + "/chapitres/" + chapitre), FORMATEUR_A, "ENSEIGNANT",
                "{\"titre\":\"Nouveau titre\",\"contenu\":\"Texte\",\"dureeMinutes\":35,\"videoUrl\":\"https://youtu.be/dQw4w9WgXcQ\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titre").value("Nouveau titre"))
                .andExpect(jsonPath("$.data.dureeMinutes").value(35))
                .andExpect(jsonPath("$.data.videoUrl").value("https://youtu.be/dQw4w9WgXcQ"));
        envoyer(put("/api/cours/" + id + "/chapitres/99999"), FORMATEUR_A, "ENSEIGNANT", CHAPITRE).andExpect(status().isNotFound());
    }

    @Test
    void liensVideoValides() throws Exception {
        long id = creerCours(FORMATEUR_A, "Vidéos");
        for (String ok : List.of(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://youtu.be/dQw4w9WgXcQ",
                "https://www.youtube.com/embed/dQw4w9WgXcQ",
                "https://cdn.exemple.tn/videos/chapitre1.mp4",
                "http://exemple.tn/v.mp4?token=abc")) {
            envoyer(post("/api/cours/" + id + "/chapitres"), FORMATEUR_A, "ENSEIGNANT", chapitreAvecVideo(ok))
                    .andExpect(status().isCreated());
        }
        for (String ko : List.of(
                "javascript:alert(1)",
                "ftp://exemple.tn/v.mp4",
                "https://exemple.tn/page.html",
                "https://vimeo.com/123456",
                "https://exemple.tn/v.mp4.exe",
                "data:text/html;base64,AAAA")) {
            envoyer(post("/api/cours/" + id + "/chapitres"), FORMATEUR_A, "ENSEIGNANT", chapitreAvecVideo(ko))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.videoUrl").exists());
        }
    }

    @Test
    void dernierChapitreDunCoursPublieNePeutPasEtreSupprime() throws Exception {
        long id = creerCours(FORMATEUR_A, "Publié");
        long chapitre = creerChapitre(FORMATEUR_A, id);
        envoyer(patch("/api/cours/" + id + "/statut"), FORMATEUR_A, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}").andExpect(status().isOk());
        envoyer(delete("/api/cours/" + id + "/chapitres/" + chapitre), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ fichiers

    @Test
    void fauxPdfRefuse() throws Exception {
        long id = creerCours(FORMATEUR_A, "PDF");
        // Texte simple renommé .pdf, avec le bon Content-Type.
        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id,
                new MockMultipartFile("fichier", "cours.pdf", "application/pdf", "ceci n'est pas un pdf".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
        // En-tête %PDF- mais contenu inexploitable.
        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id,
                new MockMultipartFile("fichier", "faux.pdf", "application/pdf", "%PDF-1.7\nn'importe quoi".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
        // Un exécutable ou une image déguisée en PDF.
        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id,
                new MockMultipartFile("fichier", "image.pdf", "application/pdf", octetsPng()))
                .andExpect(status().isBadRequest());
        lire(FORMATEUR_A, "ENSEIGNANT", id).andExpect(jsonPath("$.data.aPdf").value(false));
        assertThat(fichiers("pdf")).isEmpty();
    }

    @Test
    void envoiSansFichierRefuse() throws Exception {
        long id = creerCours(FORMATEUR_A, "Sans fichier");
        envoyer(multipart("/api/cours/" + id + "/pdf"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isBadRequest());
    }

    @Test
    void fausseImageRefusee() throws Exception {
        long id = creerCours(FORMATEUR_A, "Image");
        envoyerFichier("image", FORMATEUR_A, "ENSEIGNANT", id,
                new MockMultipartFile("fichier", "photo.png", "image/png", pdfDeTest("exemple.pdf").getBytes()))
                .andExpect(status().isBadRequest());
        envoyerFichier("image", FORMATEUR_A, "ENSEIGNANT", id,
                new MockMultipartFile("fichier", "photo.jpg", "image/jpeg", "pas une image".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
        // Signature PNG correcte mais image illisible.
        byte[] tronquee = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        envoyerFichier("image", FORMATEUR_A, "ENSEIGNANT", id, new MockMultipartFile("fichier", "x.png", "image/png", tronquee))
                .andExpect(status().isBadRequest());
        // Plus de 2 Mo.
        byte[] trop = new byte[2 * 1024 * 1024 + 1];
        byte[] png = octetsPng();
        System.arraycopy(png, 0, trop, 0, png.length);
        envoyerFichier("image", FORMATEUR_A, "ENSEIGNANT", id, new MockMultipartFile("fichier", "gros.png", "image/png", trop))
                .andExpect(status().isBadRequest());
        assertThat(fichiers("images")).isEmpty();
    }

    @Test
    void imageEnregistreeServieEtRemplacee() throws Exception {
        long id = creerCoursPublie(FORMATEUR_A, "Illustré");
        envoyerFichier("image", FORMATEUR_A, "ENSEIGNANT", id, imageDeTest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(containsString("/api/cours/" + id + "/image")));
        assertThat(fichiers("images")).hasSize(1);
        // Le nom sur disque est un UUID, pas le nom d'origine.
        assertThat(fichiers("images").get(0).getFileName().toString()).matches("[0-9a-f-]{36}\\.png").doesNotContain("couverture");

        envoyer(get("/api/cours/" + id + "/image"), APPRENANT, "ETUDIANT", null)
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        // Cours publié : lisible même sans identité (visiteur).
        mvc.perform(get("/api/cours/" + id + "/image")).andExpect(status().isOk());

        // Remplacer supprime l'ancienne image.
        envoyerFichier("image", FORMATEUR_A, "ENSEIGNANT", id, imageDeTest()).andExpect(status().isOk());
        assertThat(fichiers("images")).hasSize(1);
    }

    @Test
    void imageDunBrouillonNestPasPubliqueNiPourLesAutres() throws Exception {
        long id = creerCours(FORMATEUR_A, "Brouillon illustré");
        envoyerFichier("image", FORMATEUR_A, "ENSEIGNANT", id, imageDeTest()).andExpect(status().isOk());
        mvc.perform(get("/api/cours/" + id + "/image")).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + id + "/image"), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + id + "/image"), FORMATEUR_B, "ENSEIGNANT", null).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + id + "/image"), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
    }

    @Test
    void extractionDuTexteDunPdf() throws Exception {
        long id = creerCours(FORMATEUR_A, "Spring");
        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id, pdfDeTest("exemple.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aPdf").value(true))
                .andExpect(jsonPath("$.data.fichierPdf").value("exemple.pdf"))
                .andExpect(jsonPath("$.data.pdfTaille").isNumber());

        envoyer(get("/api/cours/" + id + "/texte"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stub").value(false))
                .andExpect(jsonPath("$.data.estScanne").value(false))
                .andExpect(jsonPath("$.data.pages.length()").value(2))
                .andExpect(jsonPath("$.data.pages[0].numero").value(1))
                .andExpect(jsonPath("$.data.pages[0].texte").value(containsString("Spring Boot")))
                .andExpect(jsonPath("$.data.texte").value(containsString("Spring Boot")))
                // Données personnelles masquées avant de sortir du service.
                .andExpect(jsonPath("$.data.texte").value(not(containsString("prof@example.com"))))
                .andExpect(jsonPath("$.data.texte").value(not(containsString("22 123 456"))))
                .andExpect(jsonPath("$.data.texte").value(containsString("[EMAIL]")))
                .andExpect(jsonPath("$.data.texte").value(containsString("[TELEPHONE]")));
        assertThat(fichiers("pdf")).hasSize(1);
    }

    @Test
    void pdfScanneSignale() throws Exception {
        long id = creerCours(FORMATEUR_A, "Scan");
        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id, pdfDeTest("scanne.pdf")).andExpect(status().isOk());
        envoyer(get("/api/cours/" + id + "/texte"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.stub").value(false))
                .andExpect(jsonPath("$.data.estScanne").value(true))
                .andExpect(jsonPath("$.data.texte").value(""));
    }

    @Test
    void coursSansPdfRenvoieUnTexteVideSansStub() throws Exception {
        long id = creerCours(FORMATEUR_A, "Sans PDF");
        envoyer(get("/api/cours/" + id + "/texte"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coursId").value(id))
                .andExpect(jsonPath("$.data.texte").value(""))
                .andExpect(jsonPath("$.data.stub").value(false))
                .andExpect(jsonPath("$.data.pages").doesNotExist())
                .andExpect(jsonPath("$.data.estScanne").doesNotExist());
    }

    @Test
    void remplacerLePdfRemplaceLesPagesEtSupprimeLAncienFichier() throws Exception {
        long id = creerCours(FORMATEUR_A, "Remplacement");
        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id, pdfDeTest("exemple.pdf")).andExpect(status().isOk());
        assertThat(pageRepository.findByCoursIdOrderByNumeroAsc(id)).hasSize(2);

        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id, pdfDeTest("scanne.pdf")).andExpect(status().isOk());
        assertThat(pageRepository.findByCoursIdOrderByNumeroAsc(id)).hasSize(1);
        assertThat(fichiers("pdf")).hasSize(1);
        envoyer(get("/api/cours/" + id + "/texte"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.pages.length()").value(1))
                .andExpect(jsonPath("$.data.estScanne").value(true));
    }

    @Test
    void telechargementDuPdfSelonLaVisibilite() throws Exception {
        long id = creerCours(FORMATEUR_A, "Téléchargement");
        creerChapitre(FORMATEUR_A, id);
        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id, pdfDeTest("exemple.pdf")).andExpect(status().isOk());

        envoyer(get("/api/cours/" + id + "/pdf"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        envoyer(get("/api/cours/" + id + "/pdf"), ADMIN, "ADMIN", null).andExpect(status().isOk());
        // Brouillon : ni l'apprenant ni un autre formateur.
        envoyer(get("/api/cours/" + id + "/pdf"), APPRENANT, "ETUDIANT", null).andExpect(status().isNotFound());
        envoyer(get("/api/cours/" + id + "/pdf"), FORMATEUR_B, "ENSEIGNANT", null).andExpect(status().isNotFound());

        envoyer(patch("/api/cours/" + id + "/statut"), FORMATEUR_A, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}").andExpect(status().isOk());
        envoyer(get("/api/cours/" + id + "/pdf"), APPRENANT, "ETUDIANT", null).andExpect(status().isOk());
    }

    @Test
    void supprimerUnCoursSupprimeSesFichiersEtSesPages() throws Exception {
        long id = creerCours(FORMATEUR_A, "À supprimer");
        envoyerFichier("pdf", FORMATEUR_A, "ENSEIGNANT", id, pdfDeTest("exemple.pdf")).andExpect(status().isOk());
        envoyerFichier("image", FORMATEUR_A, "ENSEIGNANT", id, imageDeTest()).andExpect(status().isOk());
        assertThat(fichiers("pdf")).hasSize(1);
        assertThat(fichiers("images")).hasSize(1);

        envoyer(delete("/api/cours/" + id), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        assertThat(fichiers("pdf")).isEmpty();
        assertThat(fichiers("images")).isEmpty();
        assertThat(pageRepository.findByCoursIdOrderByNumeroAsc(id)).isEmpty();
    }

    // ------------------------------------------------------------------ aides

    private static String json(String titre) {
        return "{\"titre\":\"" + titre + "\",\"description\":\"Description\",\"categorie\":\"Backend\",\"niveau\":\"DEBUTANT\"}";
    }

    private static String chapitreAvecVideo(String url) {
        return "{\"titre\":\"Vidéo\",\"dureeMinutes\":10,\"videoUrl\":\"" + url + "\"}";
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder requete, long id, String role, String corps) throws Exception {
        requete.header("X-User-Id", id).header("X-User-Role", role);
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mvc.perform(requete);
    }

    private ResultActions lire(long id, String role, long coursId) throws Exception {
        return envoyer(get("/api/cours/" + coursId), id, role, null);
    }

    private ResultActions envoyerFichier(String route, long id, String role, long coursId, MockMultipartFile fichier) throws Exception {
        return mvc.perform(multipart("/api/cours/" + coursId + "/" + route).file(fichier)
                .header("X-User-Id", id).header("X-User-Role", role));
    }

    private long creerCours(long formateur, String titre) throws Exception {
        return new MvcResultId(envoyer(post("/api/cours"), formateur, "ENSEIGNANT", json(titre)).andExpect(status().isCreated())).id;
    }

    private long creerCoursPublie(long formateur, String titre) throws Exception {
        long id = creerCours(formateur, titre);
        creerChapitre(formateur, id);
        envoyer(patch("/api/cours/" + id + "/statut"), formateur, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}").andExpect(status().isOk());
        return id;
    }

    private long creerChapitre(long formateur, long coursId) throws Exception {
        return creerChapitre(formateur, coursId, "Introduction");
    }

    private long creerChapitre(long formateur, long coursId, String titre) throws Exception {
        String corps = "{\"titre\":\"" + titre + "\",\"contenu\":\"Bienvenue\",\"dureeMinutes\":20}";
        return new MvcResultId(envoyer(post("/api/cours/" + coursId + "/chapitres"), formateur, "ENSEIGNANT", corps)
                .andExpect(status().isCreated())).id;
    }

    /** Lit {@code data.id} dans la réponse. */
    private static final class MvcResultId {
        final long id;

        MvcResultId(ResultActions actions) throws Exception {
            Number n = JsonPath.read(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
            this.id = n.longValue();
        }
    }

    private static MockMultipartFile pdfDeTest(String nom) throws IOException {
        byte[] octets = new ClassPathResource(nom).getContentAsByteArray();
        return new MockMultipartFile("fichier", nom, "application/pdf", octets);
    }

    private static MockMultipartFile imageDeTest() throws IOException {
        return new MockMultipartFile("fichier", "couverture.png", "image/png", octetsPng());
    }

    private static byte[] octetsPng() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(image, "png", sortie);
        return sortie.toByteArray();
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

    private static Path creerDossierTemporaire() {
        try {
            return Files.createTempDirectory("bawsla-cours-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

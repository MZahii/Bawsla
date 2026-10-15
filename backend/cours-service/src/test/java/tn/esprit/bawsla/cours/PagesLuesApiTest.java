package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
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
import tn.esprit.bawsla.cours.service.PdfRenduService;
import tn.esprit.bawsla.cours.service.SourceTexteService;

/**
 * Lecture des pages sans texte par vision : détection des pages scannées (moins de 30 caractères), rendu PDF en image, dépôt d'image
 * (type, taille et dimensions vérifiés), lecture asynchrone avec avancement, reprise après panne, plafond de 30 pages, correction,
 * suppression, droits. L'IA est remplacée par {@link FauxIa}.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cours_pages_lues;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PagesLuesApiTest.IaDeTest.class)
class PagesLuesApiTest {

    private static final Path STOCKAGE = dossierTemporaire();

    private static final long FORMATEUR_A = 10L;
    private static final long FORMATEUR_B = 20L;
    private static final long APPRENANT = 30L;
    private static final long ADMIN = 1L;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String QUOTA = "Lecture des images indisponible pour le moment, réessaie plus tard";

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
    @Autowired SourceTexteService sourceTexte;
    @Autowired PdfRenduService rendu;
    @Autowired TransactionTemplate transaction;

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

    // ------------------------------------------------------------------ détection et rendu

    @Test
    void pagesSansTexteDetecteesParPage() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("Cette page contient assez de texte pour être exploitable par PDFBox.", "", "   ", "court"));
        // « court » (5 caractères) est sous le seuil de 30 : il compte comme une page sans texte.
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.aLire").value(3))
                .andExpect(jsonPath("$.data.plafond").value(30))
                .andExpect(jsonPath("$.data.pages.length()").value(3))
                .andExpect(jsonPath("$.data.pages[0].numero").value(2))
                .andExpect(jsonPath("$.data.pages[0].sansTexte").value(true))
                .andExpect(jsonPath("$.data.pages[0].source").value("PDFBOX"))
                .andExpect(jsonPath("$.data.pages[0].aLire").value(true));
    }

    @Test
    void pageDePdfRendueEnPngA150Dpi() throws Exception {
        byte[] pdf = pdf("Page un avec du texte lisible", "");
        try (PdfRenduService.Rendu r = rendu.ouvrir(pdf)) {
            assertThat(r.nbPages()).isEqualTo(2);
            byte[] png = r.png(2);
            assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            // Format Lettre (612 x 792 points) à 150 dpi : 1275 x 1650 pixels.
            assertThat(image.getWidth()).isBetween(1270, 1280);
            assertThat(image.getHeight()).isBetween(1645, 1655);
        }
    }

    // ------------------------------------------------------------------ lecture asynchrone

    @Test
    void lectureDesPagesScanneesAvecAvancement() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", "", ""));

        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.etapes[0].etape").value("LECTURE_IMAGES"))
                .andExpect(jsonPath("$.data.etapes[0].total").value(3))
                .andExpect(jsonPath("$.data.etapes[0].avancement").value(0));
        attendreFin(id);

        envoyer(get(url(id, "/pages/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.enCours").value(false))
                .andExpect(jsonPath("$.data.etapes[0].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[0].avancement").value(3))
                .andExpect(jsonPath("$.data.etapes[0].total").value(3));
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.aLire").value(0))
                .andExpect(jsonPath("$.data.pages[0].source").value("VISION"))
                .andExpect(jsonPath("$.data.pages[0].texte").value(containsString("Texte lu par l'IA")))
                .andExpect(jsonPath("$.data.pages[2].texte").value(containsString("(page 3)")))
                .andExpect(jsonPath("$.data.pages[0].sansTexte").value(false));

        assertThat(ia.demandesVision).hasSize(3);
        assertThat(ia.demandesVision).allSatisfy(d -> {
            assertThat(d.mime()).isEqualTo("image/png");
            assertThat(Base64.getDecoder().decode(d.imageBase64())).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        });
        assertThat(ia.demandesVision).extracting("numero").containsExactly(1, 2, 3);
        // Authorization lu avant l'async et transmis à chaque appel ; délai de lecture propre à l'appel.
        assertThat(ia.autorisations).hasSize(3).containsOnly(JETON);
        assertThat(ia.delais).containsOnly("vision=240000");
    }

    @Test
    void panneArreteLaLectureGardeLesPagesLuesEtLaReprisePorteSurLesVides() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", "", ""));
        ia.panneVision = new ServiceUnavailableException(QUOTA);
        ia.panneVisionApres = 1; // la première page passe, la deuxième échoue

        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id);
        envoyer(get(url(id, "/pages/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[0].statut").value("ECHEC"))
                .andExpect(jsonPath("$.data.etapes[0].erreur").value(QUOTA))
                .andExpect(jsonPath("$.data.etapes[0].avancement").value(1));
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.aLire").value(2));

        // La panne passe : le nouveau lancement ne relit que les 2 pages vides.
        ia.panneVision = null;
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.etapes[0].total").value(2));
        attendreFin(id);
        assertThat(ia.demandesVision).extracting("numero").containsExactly(1, 2, 3);
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.aLire").value(0));
    }

    @Test
    void pageIllisibleEstMarqueeLueEtNEstPasRelue() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", ""));
        ia.pageVideVision = true;
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id);
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.aLire").value(0)).andExpect(jsonPath("$.data.pages[0].source").value("VISION"))
                .andExpect(jsonPath("$.data.pages[0].sansTexte").value(true));
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("Aucune page à lire")));
    }

    @Test
    void plafondDe30PagesParLancement() throws Exception {
        long id = cours();
        deposerPdf(id, pdf(new String[35]));
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.aLire").value(35));

        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.etapes[0].total").value(30));
        attendreFin(id);
        assertThat(ia.demandesVision).hasSize(30);
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.aLire").value(5));

        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.etapes[0].total").value(5));
        attendreFin(id);
        assertThat(ia.demandesVision).hasSize(35);
    }

    @Test
    void geminiIndisponibleLesPagesSuivantesVontDirectementAuLocalPuisLeLotSArreteApresLaLimite() throws Exception {
        long id = cours();
        deposerPdf(id, pdf(new String[13]));
        ia.localDepuis = 3; // pages 1 et 2 : Gemini ; à partir de la 3e, Gemini est en panne

        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id);

        // 2 pages par Gemini + 10 en local (VISION_LOCALE_PAGES_MAX = 10 par défaut), puis arrêt : 1 page restante.
        assertThat(ia.demandesVision).hasSize(12);
        assertThat(ia.demandesVision.stream().map(d -> d.forcerLocal()).toList())
                .containsExactly(false, false, false, true, true, true, true, true, true, true, true, true); // la 3e bascule, les suivantes sont forcées
        envoyer(get(url(id, "/pages/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[0].statut").value("TERMINE"))
                .andExpect(jsonPath("$.data.etapes[0].avancement").value(12))
                .andExpect(jsonPath("$.data.etapes[0].erreur").value("1 page restante : clique à nouveau sur Lire avec l'IA"));
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.aLire").value(1))
                .andExpect(jsonPath("$.data.pages[0].moteur").value("gemini"))
                .andExpect(jsonPath("$.data.pages[2].moteur").value("local · Florence-2"))
                .andExpect(jsonPath("$.data.pages[2].texte").value(containsString("locale")))
                .andExpect(jsonPath("$.data.pages[12].moteur").doesNotExist());

        // Le relancement lit la dernière page (en Gemini : plus de panne).
        ia.localDepuis = 0;
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.etapes[0].total").value(1));
        attendreFin(id);
        envoyer(get(url(id, "/pages")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.aLire").value(0))
                .andExpect(jsonPath("$.data.pages[12].moteur").value("gemini"));
    }

    @Test
    void lePremierLotSansPanneNeForcePasLeLocalEtUnePageCorrigeeNAffichePlusLeMoteur() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", ""));
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id);
        assertThat(ia.demandesVision).extracting("forcerLocal").containsOnly(false);
        envoyer(get(url(id, "/pages/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.etapes[0].erreur").doesNotExist());
        envoyer(put(url(id, "/pages/1")), FORMATEUR_A, "ENSEIGNANT", "{\"texte\":\"Texte corrigé à la main par le formateur.\"}")
                .andExpect(jsonPath("$.data.moteur").doesNotExist());
    }

    @Test
    void unSeulTraitementALaFois() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", ""));
        CountDownLatch verrou = new CountDownLatch(1);
        ia.blocageVision = verrou;
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isConflict());
        envoyer(get(url(id, "/pages/traitement")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(jsonPath("$.data.enCours").value(true));
        verrou.countDown();
        attendreFin(id);
    }

    @Test
    void sansLancementLeSuiviEstVide() throws Exception {
        long id = cours();
        envoyer(get(url(id, "/pages/traitement")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.etapes.length()").value(0));
    }

    // ------------------------------------------------------------------ dépôt d'image

    @Test
    void imageDeposeeDevientLaPageSuivanteEtEstLue() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("Une page de PDF avec assez de texte exploitable.", ""));

        envoyer(multipart(url(id, "/images")).file(fichier("photo.png", png(60, 40))), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.numero").value(3))
                .andExpect(jsonPath("$.data.aImage").value(true))
                .andExpect(jsonPath("$.data.aLire").value(true))
                .andExpect(jsonPath("$.data.sansTexte").value(true));
        envoyer(multipart(url(id, "/images")).file(fichier("photo.jpg", jpeg(60, 40))), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.numero").value(4));

        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id);
        assertThat(ia.demandesVision).extracting("numero").containsExactly(2, 3, 4);
        assertThat(ia.demandesVision).extracting("mime").containsExactly("image/png", "image/png", "image/jpeg");

        byte[] apercu = envoyer(get(url(id, "/pages/3/image")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        assertThat(apercu).isEqualTo(png(60, 40));
        envoyer(get(url(id, "/pages/2/image")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isNotFound()); // page du PDF : pas d'image
        assertThat(fichiers("pages")).hasSize(2);
    }

    @Test
    void imageRefuseeSiMauvaisTypeOuTropGrosse() throws Exception {
        long id = cours();
        byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        envoyer(multipart(url(id, "/images")).file(fichier("anim.gif", gif)), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("JPG ou PNG")));
        envoyer(multipart(url(id, "/images")).file(fichier("faux.png", "ceci n'est pas une image du tout".getBytes(StandardCharsets.UTF_8))),
                FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isBadRequest());
        // Signature PNG correcte mais contenu corrompu.
        byte[] corrompue = png(20, 20);
        for (int i = 40; i < corrompue.length - 20; i++) {
            corrompue[i] = 0;
        }
        envoyer(multipart(url(id, "/images")).file(fichier("corrompue.png", corrompue)), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isBadRequest());
        // Plus de 5 Mo.
        byte[] lourde = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(png(10, 10), 0, lourde, 0, 8);
        envoyer(multipart(url(id, "/images")).file(fichier("lourde.png", lourde)), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("5 Mo")));
        // Image trop large (en-tête lu avant tout décodage).
        envoyer(multipart(url(id, "/images")).file(fichier("large.png", png(8001, 1))), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("trop grande")));
        envoyer(multipart(url(id, "/images")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isBadRequest());
        assertThat(pageRepository.count()).isZero();
        assertThat(fichiers("pages")).isEmpty();
    }

    @Test
    void imageSeuleSansPdfAlimenteLesGenerations() throws Exception {
        long id = cours();
        envoyer(multipart(url(id, "/images")).file(fichier("tableau.png", png(80, 60))), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isCreated());
        ia.texteVision = "Définition : la dérivée d'une fonction mesure la variation instantanée de cette fonction en un point donné. "
                + "Elle se calcule comme la limite du taux d'accroissement quand l'écart entre les deux points tend vers zéro. "
                + "Exemple : la dérivée de x au carré est 2x, ce qui donne la pente de la tangente en chaque point de la courbe.";
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id);

        // Hors de la requête HTTP, les chapitres du cours se chargent à la demande : on lit dans une transaction.
        String texteCours = transaction.execute(s -> sourceTexte.pour(coursRepository.findById(id).orElseThrow()));
        assertThat(texteCours).contains("la dérivée d'une fonction mesure");
        var pages = transaction.execute(s -> sourceTexte.pagesPour(coursRepository.findById(id).orElseThrow()));
        assertThat(pages.pages()).hasSize(1);
        assertThat(pages.pages().get(0).numero()).isEqualTo(1);
        assertThat(pages.pages().get(0).texte()).contains("dérivée");
        // Le contrat /texte voit aussi ces pages.
        envoyer(get("/api/cours/" + id + "/texte"), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(jsonPath("$.data.pages.length()").value(1)).andExpect(jsonPath("$.data.estScanne").value(false));
    }

    @Test
    void lePdfScanneLuParVisionAlimenteLesGenerations() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", "", ""));
        // Avant la lecture : un PDF scanné n'a rien à expliquer (erreur claire, aucun appel à l'IA).
        org.junit.jupiter.api.Assertions.assertThrows(tn.esprit.bawsla.cours.common.exception.BadRequestException.class,
                () -> transaction.execute(s -> sourceTexte.pagesPour(coursRepository.findById(id).orElseThrow())));

        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id);
        var pages = transaction.execute(s -> sourceTexte.pagesPour(coursRepository.findById(id).orElseThrow()));
        assertThat(pages.pages()).hasSize(3).allSatisfy(p -> assertThat(p.texte()).contains("Texte lu par l'IA"));
    }

    // ------------------------------------------------------------------ correction, suppression, PDF remplacé

    @Test
    void leFormateurCorrigeLeTexteLu() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", ""));
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isAccepted());
        attendreFin(id);

        envoyer(put(url(id, "/pages/2")), FORMATEUR_A, "ENSEIGNANT", "{\"texte\":\"  Texte corrigé par le formateur : la limite de f(x) quand x tend vers 2 vaut 4.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.texte").value("Texte corrigé par le formateur : la limite de f(x) quand x tend vers 2 vaut 4."))
                .andExpect(jsonPath("$.data.modifie").value(true))
                .andExpect(jsonPath("$.data.source").value("VISION"));
        envoyer(put(url(id, "/pages/9")), FORMATEUR_A, "ENSEIGNANT", "{\"texte\":\"x\"}").andExpect(status().isNotFound());
        envoyer(put(url(id, "/pages/2")), FORMATEUR_A, "ENSEIGNANT", "{}").andExpect(status().isBadRequest());
        // La page corrigée n'est jamais relue par l'IA.
        envoyer(post(url(id, "/pages/lire")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isBadRequest());
    }

    @Test
    void supprimerUneImageRenumeroteLesSuivantesEtEfface() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("Page de PDF avec suffisamment de texte pour l'extraction."));
        envoyer(multipart(url(id, "/images")).file(fichier("a.png", png(30, 30))), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isCreated());
        envoyer(multipart(url(id, "/images")).file(fichier("b.png", png(31, 31))), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isCreated());
        envoyer(multipart(url(id, "/images")).file(fichier("c.png", png(32, 32))), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isCreated());
        assertThat(fichiers("pages")).hasSize(3);

        envoyer(delete(url(id, "/pages/3")), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());

        assertThat(pageRepository.findByCoursIdOrderByNumeroAsc(id)).extracting("numero").containsExactly(1, 2, 3);
        assertThat(fichiers("pages")).hasSize(2);
        byte[] ancienneC = envoyer(get(url(id, "/pages/3/image")), FORMATEUR_A, "ENSEIGNANT", null).andReturn().getResponse().getContentAsByteArray();
        assertThat(ancienneC).isEqualTo(png(32, 32)); // l'image C est devenue la page 3
        envoyer(delete(url(id, "/pages/1")), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("pages du PDF")));
    }

    @Test
    void nouveauPdfConserveLesImagesRenumeroteesALaSuite() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", "", "", ""));
        envoyer(multipart(url(id, "/images")).file(fichier("photo.png", png(30, 30))), FORMATEUR_A, "ENSEIGNANT", null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.numero").value(5));

        deposerPdf(id, pdf("", ""));

        assertThat(pageRepository.findByCoursIdOrderByNumeroAsc(id)).extracting("numero").containsExactly(1, 2, 3);
        assertThat(pageRepository.findByCoursIdAndImageStockageIsNotNullOrderByNumeroAsc(id)).extracting("numero").containsExactly(3);
    }

    @Test
    void supprimerLeCoursSupprimeLesImages() throws Exception {
        long id = cours();
        envoyer(multipart(url(id, "/images")).file(fichier("photo.png", png(30, 30))), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isCreated());
        assertThat(fichiers("pages")).hasSize(1);
        envoyer(delete("/api/cours/" + id), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isOk());
        assertThat(fichiers("pages")).isEmpty();
        assertThat(pageRepository.findByCoursIdOrderByNumeroAsc(id)).isEmpty();
    }

    // ------------------------------------------------------------------ droits

    @Test
    void seulLeProprietaireOuLAdminAgit() throws Exception {
        long id = cours();
        deposerPdf(id, pdf("", ""));
        envoyer(multipart(url(id, "/images")).file(fichier("photo.png", png(30, 30))), FORMATEUR_A, "ENSEIGNANT", null).andExpect(status().isCreated());

        for (long intrus : new long[] {FORMATEUR_B, APPRENANT}) {
            String role = intrus == APPRENANT ? "ETUDIANT" : "ENSEIGNANT";
            envoyer(get(url(id, "/pages")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(get(url(id, "/pages/traitement")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(post(url(id, "/pages/lire")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(multipart(url(id, "/images")).file(fichier("x.png", png(30, 30))), intrus, role, null).andExpect(status().isForbidden());
            envoyer(put(url(id, "/pages/1")), intrus, role, "{\"texte\":\"piraté\"}").andExpect(status().isForbidden());
            envoyer(delete(url(id, "/pages/3")), intrus, role, null).andExpect(status().isForbidden());
            envoyer(get(url(id, "/pages/3/image")), intrus, role, null).andExpect(status().isForbidden());
        }
        assertThat(ia.demandesVision).isEmpty();
        envoyer(get(url(id, "/pages")), ADMIN, "ADMIN", null).andExpect(status().isOk());
        envoyer(post(url(id, "/pages/lire")), ADMIN, "ADMIN", null).andExpect(status().isAccepted());
        attendreFin(id);
        mvc.perform(get(url(id, "/pages"))).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ aides

    private static String url(long coursId, String route) {
        return "/api/cours/" + coursId + route;
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder requete, long id, String role, String corps) throws Exception {
        requete.header("X-User-Id", id).header("X-User-Role", role).header("Authorization", JETON);
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mvc.perform(requete);
    }

    private long cours() throws Exception {
        String corps = "{\"titre\":\"Calculus\",\"categorie\":\"Mathématiques\",\"niveau\":\"DEBUTANT\"}";
        Number n = JsonPath.read(envoyer(post("/api/cours"), FORMATEUR_A, "ENSEIGNANT", corps).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
        return n.longValue();
    }

    private void deposerPdf(long id, byte[] pdf) throws Exception {
        mvc.perform(multipart("/api/cours/" + id + "/pdf").file(new MockMultipartFile("fichier", "scan.pdf", "application/pdf", pdf))
                .header("X-User-Id", FORMATEUR_A).header("X-User-Role", "ENSEIGNANT")).andExpect(status().isOk());
    }

    private static MockMultipartFile fichier(String nom, byte[] octets) {
        return new MockMultipartFile("fichier", nom, "image/png", octets);
    }

    /** PDF de Lettre : une page par argument ; texte null ou vide = page sans texte (comme un scan). */
    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String texte : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (texte != null && !texte.isBlank()) {
                    try (PDPageContentStream flux = new PDPageContentStream(document, page)) {
                        flux.beginText();
                        flux.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        flux.newLineAtOffset(50, 700);
                        flux.showText(texte);
                        flux.endText();
                    }
                }
            }
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            document.save(sortie);
            return sortie.toByteArray();
        }
    }

    private static byte[] png(int largeur, int hauteur) throws IOException {
        return image(largeur, hauteur, "png");
    }

    private static byte[] jpeg(int largeur, int hauteur) throws IOException {
        return image(largeur, hauteur, "jpg");
    }

    private static byte[] image(int largeur, int hauteur, String format) throws IOException {
        BufferedImage image = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < largeur; x++) {
            for (int y = 0; y < hauteur; y++) {
                image.setRGB(x, y, (x * 7 + y * 13) % 255 << 16 | (x * 3) % 255 << 8 | (y * 5) % 255);
            }
        }
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(image, format, sortie);
        return sortie.toByteArray();
    }

    /** Attend la fin du dernier traitement de lecture (au plus 20 s) : il s'exécute dans un autre thread. */
    private void attendreFin(long coursId) throws Exception {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            var dernier = traitementRepository.findFirstByCoursIdAndPorteeOrderByIdDesc(coursId, TraitementIA.PORTEE_VISION);
            if (dernier.isPresent()) {
                var lignes = traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, dernier.get().getExecution());
                if (lignes.stream().noneMatch(l -> l.getStatut().actif())) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("La lecture n'est pas terminée après 20 s");
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
            return Files.createTempDirectory("bawsla-cours-pages-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

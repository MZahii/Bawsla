package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Year;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import tn.esprit.bawsla.cours.repository.ChapitreTermineRepository;
import tn.esprit.bawsla.cours.repository.CommandeRepository;
import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.InscriptionRepository;

/**
 * Paiement simulé d'un cours payant : contrôle de la carte, commande idempotente qui inscrit l'apprenant, contenu réservé aux acheteurs
 * (programme seul sinon), achats et ventes. Les en-têtes X-User-* sont ceux que poserait la gateway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommandeApiTest {

    private static final long FORMATEUR_A = 10L;
    private static final long FORMATEUR_B = 20L;
    private static final long APPRENANT = 30L;
    private static final long AUTRE_APPRENANT = 31L;
    private static final long ADMIN = 1L;

    private static final String VISA = "4242 4242 4242 4242";
    private static final String MASTERCARD = "5555555555554444";
    private static final String EXPIRATION = "12/" + String.format("%02d", (Year.now().getValue() + 2) % 100);

    @Autowired MockMvc mvc;
    @Autowired CoursRepository coursRepository;
    @Autowired InscriptionRepository inscriptionRepository;
    @Autowired ChapitreTermineRepository termineRepository;
    @Autowired CommandeRepository commandeRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void nettoyer() {
        commandeRepository.deleteAll();
        termineRepository.deleteAll();
        inscriptionRepository.deleteAll();
        coursRepository.deleteAll();
    }

    // ------------------------------------------------------------------ payer

    @Test
    void paiementAccepteCreeLaCommandeEtInscritLApprenant() throws Exception {
        long cours = coursPayant(FORMATEUR_A);

        payer(cours, APPRENANT, VISA, EXPIRATION, "123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Paiement simulé accepté"))
                .andExpect(jsonPath("$.data.reference").value(matchesPattern("BW-\\d{8}-[0-9A-F]{6}")))
                .andExpect(jsonPath("$.data.coursId").value(cours))
                .andExpect(jsonPath("$.data.coursTitre").value("Payant"))
                .andExpect(jsonPath("$.data.montant").value(49.5))
                .andExpect(jsonPath("$.data.devise").value("TND"))
                .andExpect(jsonPath("$.data.carte").value("Visa •••• 4242"))
                .andExpect(jsonPath("$.data.simule").value(true));

        assertThat(inscriptionRepository.findByEtudiantIdAndCoursId(APPRENANT, cours)).isPresent();
        envoyer(get(route(cours, "/progression")), APPRENANT, "ETUDIANT").andExpect(jsonPath("$.data.progression").value(0.0));
    }

    @Test
    void secondPaiementIdempotentSansSecondeCommande() throws Exception {
        long cours = coursPayant(FORMATEUR_A);
        String reference = JsonPath.read(corps(payer(cours, APPRENANT, VISA, EXPIRATION, "123").andExpect(status().isCreated())),
                "$.data.reference");

        payer(cours, APPRENANT, MASTERCARD, EXPIRATION, "456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cours déjà acheté"))
                .andExpect(jsonPath("$.data.reference").value(reference));
        assertThat(commandeRepository.count()).isEqualTo(1);

        // Désinscrit puis rachète : pas de nouveau paiement, mais l'inscription revient.
        envoyer(delete(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isOk());
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isCreated()); // déjà payé : permis
        assertThat(commandeRepository.count()).isEqualTo(1);
    }

    @Test
    void carteInvalideExpireeOuRefuseeNeCreeRien() throws Exception {
        long cours = coursPayant(FORMATEUR_A);

        payer(cours, APPRENANT, "4242 4242 4242 4241", EXPIRATION, "123") // Luhn faux
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Numéro de carte invalide."));
        payer(cours, APPRENANT, VISA, "01/20", "123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cette carte a expiré."));
        payer(cours, APPRENANT, "4000 0000 0000 0002", EXPIRATION, "123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("refusé")));
        // Format : validation du DTO (400 avec détails).
        payer(cours, APPRENANT, "abcd", "13/30", "12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.numeroCarte").exists())
                .andExpect(jsonPath("$.details.expiration").exists())
                .andExpect(jsonPath("$.details.cryptogramme").exists());

        assertThat(commandeRepository.count()).isZero();
        assertThat(inscriptionRepository.count()).isZero();
    }

    @Test
    void seulUnApprenantAchete_etPasUnCoursGratuit() throws Exception {
        long payant = coursPayant(FORMATEUR_A);
        payer(payant, FORMATEUR_B, VISA, EXPIRATION, "123").andExpect(status().isForbidden());
        payer(payant, ADMIN, VISA, EXPIRATION, "123").andExpect(status().isForbidden());

        long gratuit = coursPublie(FORMATEUR_A);
        payer(gratuit, APPRENANT, VISA, EXPIRATION, "123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("gratuit")));
        payer(999_999, APPRENANT, VISA, EXPIRATION, "123").andExpect(status().isNotFound());
        assertThat(commandeRepository.count()).isZero();
    }

    @Test
    void seulsLesQuatreDerniersChiffresSontStockes() throws Exception {
        long cours = coursPayant(FORMATEUR_A);
        payer(cours, APPRENANT, VISA, EXPIRATION, "123").andExpect(status().isCreated());

        String ligne = jdbc.queryForMap("select * from commande").toString();
        assertThat(ligne).doesNotContain("4242424242424242").doesNotContain("123,").contains("4242");
        assertThat(commandeRepository.findAll().get(0).getDerniersChiffres()).isEqualTo("4242");
    }

    // ------------------------------------------------------------------ contenu réservé

    @Test
    void contenuDUnCoursPayantReserveAuxAcheteurs() throws Exception {
        long cours = coursPayant(FORMATEUR_A);

        // Visiteur et apprenant non acheteur : programme seul, pas de contenu ni de vidéo ; PDF et fiche refusés.
        mvc.perform(get(route(cours, "/chapitres")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].titre").value("Chapitre 1"))
                .andExpect(jsonPath("$.data[0].contenu").doesNotExist());
        envoyer(get(route(cours, "/chapitres")), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data[0].contenu").doesNotExist())
                .andExpect(jsonPath("$.data[0].videoUrl").doesNotExist());
        envoyer(get(route(cours, "/pdf")), APPRENANT, "ETUDIANT")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(containsString("payant")));
        envoyer(get(route(cours, "/fiche")), APPRENANT, "ETUDIANT").andExpect(status().isForbidden());
        // Un autre formateur n'a pas acheté non plus.
        envoyer(get(route(cours, "/chapitres")), FORMATEUR_B, "ENSEIGNANT").andExpect(jsonPath("$.data[0].contenu").doesNotExist());

        // Le propriétaire et l'ADMIN lisent tout.
        envoyer(get(route(cours, "/chapitres")), FORMATEUR_A, "ENSEIGNANT").andExpect(jsonPath("$.data[0].contenu").value("Contenu 1"));
        envoyer(get(route(cours, "/chapitres")), ADMIN, "ADMIN").andExpect(jsonPath("$.data[0].contenu").value("Contenu 1"));

        // Après achat : tout le contenu ; la fiche devient lisible (pas encore générée, mais plus 403).
        payer(cours, APPRENANT, VISA, EXPIRATION, "123").andExpect(status().isCreated());
        envoyer(get(route(cours, "/chapitres")), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data[0].contenu").value("Contenu 1"))
                .andExpect(jsonPath("$.data[0].videoUrl").value("https://www.youtube.com/watch?v=abcdef"));
        envoyer(get(route(cours, "/fiche")), APPRENANT, "ETUDIANT").andExpect(status().isOk());
        // L'autre apprenant, lui, n'a toujours que le programme.
        envoyer(get(route(cours, "/chapitres")), AUTRE_APPRENANT, "ETUDIANT").andExpect(jsonPath("$.data[0].contenu").doesNotExist());
    }

    @Test
    void coursGratuitLisibleParTout_apprenantConnecte() throws Exception {
        long cours = coursPublie(FORMATEUR_A);
        envoyer(get(route(cours, "/chapitres")), APPRENANT, "ETUDIANT").andExpect(jsonPath("$.data[0].contenu").value("Contenu 1"));
        mvc.perform(get(route(cours, "/chapitres"))).andExpect(jsonPath("$.data[0].contenu").doesNotExist()); // visiteur : programme
    }

    // ------------------------------------------------------------------ achats et ventes

    @Test
    void mesAchatsEtVentesSelonLeRole() throws Exception {
        long coursA = coursPayant(FORMATEUR_A);
        long coursB = coursPayant(FORMATEUR_B);
        payer(coursA, APPRENANT, VISA, EXPIRATION, "123").andExpect(status().isCreated());
        payer(coursB, APPRENANT, MASTERCARD, EXPIRATION, "123").andExpect(status().isCreated());
        payer(coursA, AUTRE_APPRENANT, VISA, EXPIRATION, "123").andExpect(status().isCreated());

        envoyer(get("/api/cours/commandes/mes"), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].coursId").value(coursB)) // plus récent d'abord
                .andExpect(jsonPath("$.data[0].carte").value("Mastercard •••• 4444"));
        envoyer(get("/api/cours/commandes/mes"), AUTRE_APPRENANT, "ETUDIANT").andExpect(jsonPath("$.data.length()").value(1));

        envoyer(get("/api/cours/commandes/ventes"), ADMIN, "ADMIN").andExpect(jsonPath("$.data.length()").value(3));
        envoyer(get("/api/cours/commandes/ventes"), FORMATEUR_A, "ENSEIGNANT")
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].coursId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is((int) coursA))));
        envoyer(get("/api/cours/commandes/ventes"), 99L, "ENSEIGNANT").andExpect(jsonPath("$.data.length()").value(0));
        envoyer(get("/api/cours/commandes/ventes"), APPRENANT, "ETUDIANT").andExpect(status().isForbidden());

        // Cours supprimé : la commande reste (trace d'achat), sans titre.
        envoyer(delete("/api/cours/" + coursB), FORMATEUR_B, "ENSEIGNANT").andExpect(status().isOk());
        envoyer(get("/api/cours/commandes/mes"), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].coursTitre").doesNotExist());
    }

    // ------------------------------------------------------------------ outils

    private ResultActions payer(long cours, long apprenant, String carte, String expiration, String cvc) throws Exception {
        String json = "{\"numeroCarte\":\"" + carte + "\",\"expiration\":\"" + expiration + "\",\"cryptogramme\":\"" + cvc + "\"}";
        String role = apprenant == ADMIN ? "ADMIN" : apprenant == FORMATEUR_A || apprenant == FORMATEUR_B ? "ENSEIGNANT" : "ETUDIANT";
        return envoyer(post(route(cours, "/commandes")), apprenant, role, json);
    }

    private static String route(long coursId, String suite) {
        return "/api/cours/" + coursId + suite;
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder requete, long id, String role) throws Exception {
        return envoyer(requete, id, role, null);
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder requete, long id, String role, String corps) throws Exception {
        requete.header("X-User-Id", id).header("X-User-Role", role);
        if (corps != null) {
            requete.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mvc.perform(requete);
    }

    private static String corps(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Cours publié de 2 chapitres avec contenu et vidéo, gratuit. */
    private long coursPublie(long formateur) throws Exception {
        String json = "{\"titre\":\"Cours\",\"description\":\"Description\",\"categorie\":\"Informatique\",\"niveau\":\"DEBUTANT\"}";
        long id = ((Number) JsonPath.read(corps(envoyer(post("/api/cours"), formateur, "ENSEIGNANT", json).andExpect(status().isCreated())),
                "$.data.id")).longValue();
        for (int i = 1; i <= 2; i++) {
            envoyer(post(route(id, "/chapitres")), formateur, "ENSEIGNANT", "{\"titre\":\"Chapitre " + i + "\",\"contenu\":\"Contenu " + i
                    + "\",\"dureeMinutes\":10,\"videoUrl\":\"https://www.youtube.com/watch?v=abcdef\"}").andExpect(status().isCreated());
        }
        envoyer(patch(route(id, "/statut")), formateur, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}").andExpect(status().isOk());
        return id;
    }

    private long coursPayant(long formateur) throws Exception {
        long id = coursPublie(formateur);
        envoyer(put("/api/cours/" + id), formateur, "ENSEIGNANT",
                "{\"titre\":\"Payant\",\"description\":\"d\",\"categorie\":\"Informatique\",\"niveau\":\"DEBUTANT\",\"prix\":49.5}")
                .andExpect(status().isOk());
        return id;
    }
}

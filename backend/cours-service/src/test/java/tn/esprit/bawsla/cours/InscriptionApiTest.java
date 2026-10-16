package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.InscriptionRepository;

/**
 * Inscriptions des apprenants (lot 1) : inscription idempotente, progression par chapitre, contrat 7, nbInscrits, nettoyage à la
 * suppression d'un chapitre, d'un cours ou d'une inscription. Les en-têtes X-User-* sont ceux que poserait la gateway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InscriptionApiTest {

    private static final long FORMATEUR_A = 10L;
    private static final long FORMATEUR_B = 20L;
    private static final long APPRENANT = 30L;
    private static final long AUTRE_APPRENANT = 31L;
    private static final long ADMIN = 1L;

    @Autowired MockMvc mvc;
    @Autowired CoursRepository coursRepository;
    @Autowired InscriptionRepository inscriptionRepository;
    @Autowired ChapitreTermineRepository termineRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void nettoyer() {
        termineRepository.deleteAll();
        inscriptionRepository.deleteAll();
        coursRepository.deleteAll();
    }

    // ------------------------------------------------------------------ s'inscrire

    @Test
    void inscriptionCreeeEn201PuisIdempotenteEn200() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 2);

        ResultActions premiere = envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.etudiantId").value(APPRENANT))
                .andExpect(jsonPath("$.data.coursId").value(cours))
                .andExpect(jsonPath("$.data.progression").value(0.0))
                .andExpect(jsonPath("$.data.termine").value(false))
                .andExpect(jsonPath("$.data.dateInscription").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                // Lot 2 : le temps passé commence à 0 ; pas encore d'activité : date OMISE.
                .andExpect(jsonPath("$.data.tempsPasseMinutes").value(0))
                .andExpect(jsonPath("$.data.dateDerniereActivite").doesNotExist());
        String date = JsonPath.read(corps(premiere), "$.data.dateInscription");

        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dateInscription").value(date)); // l'existante, pas une nouvelle
        assertThat(inscriptionRepository.count()).isEqualTo(1);
    }

    @Test
    void doubleClicSimultaneSansDoublon() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        int clics = 6;
        ExecutorService pool = Executors.newFixedThreadPool(clics);
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Integer>> codes = new ArrayList<>();
        for (int i = 0; i < clics; i++) {
            codes.add(pool.submit(() -> {
                depart.await();
                return envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andReturn().getResponse().getStatus();
            }));
        }
        depart.countDown();
        int creations = 0;
        for (Future<Integer> code : codes) {
            int c = code.get();
            assertThat(c).isIn(200, 201); // jamais 409 ni 500
            if (c == 201) {
                creations++;
            }
        }
        pool.shutdown();
        assertThat(creations).isEqualTo(1);
        assertThat(inscriptionRepository.count()).isEqualTo(1);
    }

    @Test
    void seulUnApprenantSInscrit() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(post(route(cours, "/inscriptions")), FORMATEUR_B, "ENSEIGNANT").andExpect(status().isForbidden());
        envoyer(post(route(cours, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isForbidden()); // même le propriétaire
        envoyer(post(route(cours, "/inscriptions")), ADMIN, "ADMIN").andExpect(status().isForbidden());
        assertThat(inscriptionRepository.count()).isZero();
    }

    @Test
    void coursInexistantBrouillonOuArchiveRepondent404() throws Exception {
        envoyer(post(route(999_999, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());

        long brouillon = coursBrouillon(FORMATEUR_A, 1);
        envoyer(post(route(brouillon, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());

        long archive = coursPublie(FORMATEUR_A, 1);
        envoyer(patch(route(archive, "/statut")), FORMATEUR_A, "ENSEIGNANT", "{\"statut\":\"ARCHIVE\"}").andExpect(status().isOk());
        envoyer(post(route(archive, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());
        assertThat(inscriptionRepository.count()).isZero();
    }

    @Test
    void inscriptionPermisePourUnCoursPayant() throws Exception {
        // Le paiement n'existe pas dans le projet : tous les cours publiés, gratuits ou payants, acceptent l'inscription.
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(put("/api/cours/" + cours), FORMATEUR_A, "ENSEIGNANT",
                "{\"titre\":\"Payant\",\"description\":\"d\",\"categorie\":\"Informatique\",\"niveau\":\"DEBUTANT\",\"prix\":49.5}")
                .andExpect(status().isOk());
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ se désinscrire

    @Test
    void desinscriptionSupprimeLInscriptionEtSesChapitresTermines() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 2);
        List<Long> chapitres = chapitres(cours);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isCreated());
        envoyer(post(route(cours, "/chapitres/" + chapitres.get(0) + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isOk());
        envoyer(post(route(cours, "/inscriptions")), AUTRE_APPRENANT, "ETUDIANT").andExpect(status().isCreated());
        envoyer(post(route(cours, "/chapitres/" + chapitres.get(0) + "/terminer")), AUTRE_APPRENANT, "ETUDIANT").andExpect(status().isOk());
        assertThat(termineRepository.count()).isEqualTo(2);

        envoyer(delete(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isOk());

        assertThat(inscriptionRepository.findAll()).extracting("etudiantId").containsExactly(AUTRE_APPRENANT);
        assertThat(termineRepository.count()).isEqualTo(1); // ceux de l'autre apprenant restent
        envoyer(get("/api/cours/mes-inscriptions"), APPRENANT, "ETUDIANT").andExpect(jsonPath("$.data.length()").value(0));
        // Idempotent : se désinscrire sans être inscrit n'est pas une erreur.
        envoyer(delete(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isOk());
        // Se réinscrire repart de zéro.
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.progression").value(0.0));
    }

    @Test
    void seulUnApprenantSeDesinscrit() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(delete(route(cours, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isForbidden());
        envoyer(delete(route(cours, "/inscriptions")), ADMIN, "ADMIN").andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ progression

    @Test
    void progressionPartielleCompleteEtAnnulation() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 4);
        List<Long> c = chapitres(cours);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isCreated());

        envoyer(post(route(cours, "/chapitres/" + c.get(0) + "/terminer")), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progression").value(0.25))
                .andExpect(jsonPath("$.data.termine").value(false))
                .andExpect(jsonPath("$.data.dateDerniereActivite").exists())
                .andExpect(jsonPath("$.data.tempsPasseMinutes").value(0));
        // Idempotent : le même chapitre deux fois ne compte qu'une fois.
        envoyer(post(route(cours, "/chapitres/" + c.get(0) + "/terminer")), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.progression").value(0.25));
        assertThat(termineRepository.count()).isEqualTo(1);

        for (int i = 1; i < 4; i++) {
            envoyer(post(route(cours, "/chapitres/" + c.get(i) + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isOk());
        }
        envoyer(get(route(cours, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT")
                .andExpect(jsonPath("$.data[0].progression").value(1.0))
                .andExpect(jsonPath("$.data[0].termine").value(true));
        assertThat(inscriptionRepository.findAll().get(0).isTermine()).isTrue();

        envoyer(delete(route(cours, "/chapitres/" + c.get(3) + "/terminer")), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progression").value(0.75))
                .andExpect(jsonPath("$.data.termine").value(false));
        assertThat(inscriptionRepository.findAll().get(0).isTermine()).isFalse();
        // Annuler un chapitre non terminé : sans effet, sans erreur.
        envoyer(delete(route(cours, "/chapitres/" + c.get(3) + "/terminer")), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.progression").value(0.75));
    }

    @Test
    void dateDerniereActiviteMiseAJourQuandUnChapitreEstTermine() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 2);
        List<Long> c = chapitres(cours);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        assertThat(inscriptionRepository.findAll().get(0).getDateDerniereActivite()).isNull();

        envoyer(post(route(cours, "/chapitres/" + c.get(0) + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isOk());

        assertThat(inscriptionRepository.findAll().get(0).getDateDerniereActivite()).isNotNull();
        assertThat(inscriptionRepository.findAll().get(0).getTempsPasseMinutes()).isZero(); // lot 2 : seul le lecteur compte le temps
    }

    @Test
    void coursSansChapitreProgressionZeroEtNonTermine() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isCreated());
        // On dépublie pour pouvoir retirer le dernier chapitre : le cours n'a plus aucun chapitre.
        envoyer(patch(route(cours, "/statut")), FORMATEUR_A, "ENSEIGNANT", "{\"statut\":\"BROUILLON\"}").andExpect(status().isOk());
        envoyer(delete(route(cours, "/chapitres/" + chapitres(cours).get(0))), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isOk());

        envoyer(get(route(cours, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT")
                .andExpect(jsonPath("$.data[0].progression").value(0.0))
                .andExpect(jsonPath("$.data[0].termine").value(false));
    }

    @Test
    void chapitreAjouteFaitBaisserLaProgressionEtRepasserTermineAFalse() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 2);
        List<Long> c = chapitres(cours);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/chapitres/" + c.get(0) + "/terminer")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/chapitres/" + c.get(1) + "/terminer")), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data.termine").value(true));

        envoyer(post(route(cours, "/chapitres")), FORMATEUR_A, "ENSEIGNANT", "{\"titre\":\"Nouveau\",\"dureeMinutes\":5}").andExpect(status().isCreated());

        envoyer(get(route(cours, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT")
                .andExpect(jsonPath("$.data[0].progression").value(0.6667))
                .andExpect(jsonPath("$.data[0].termine").value(false));
    }

    @Test
    void chapitreSupprimeEffaceSesChapitresTermines() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 3);
        List<Long> c = chapitres(cours);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/chapitres/" + c.get(0) + "/terminer")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/chapitres/" + c.get(1) + "/terminer")), APPRENANT, "ETUDIANT");
        assertThat(termineRepository.count()).isEqualTo(2);

        envoyer(delete(route(cours, "/chapitres/" + c.get(1))), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isOk());

        assertThat(termineRepository.findAll()).extracting("chapitreId").containsExactly(c.get(0));
        envoyer(get(route(cours, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT")
                .andExpect(jsonPath("$.data[0].progression").value(0.5)); // 1 chapitre terminé sur les 2 restants
    }

    @Test
    void erreursPourTerminerUnChapitre() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        long chapitre = chapitres(cours).get(0);
        long autreCours = coursPublie(FORMATEUR_B, 1);
        long chapitreEtranger = chapitres(autreCours).get(0);

        // Pas inscrit : 403 avec un message clair.
        envoyer(post(route(cours, "/chapitres/" + chapitre + "/terminer")), APPRENANT, "ETUDIANT")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(containsString("Inscris-toi")));
        envoyer(delete(route(cours, "/chapitres/" + chapitre + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isForbidden());

        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isCreated());
        // Rôle non apprenant : 403.
        envoyer(post(route(cours, "/chapitres/" + chapitre + "/terminer")), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isForbidden());
        envoyer(post(route(cours, "/chapitres/" + chapitre + "/terminer")), ADMIN, "ADMIN").andExpect(status().isForbidden());
        // Chapitre d'un autre cours ou inexistant : 404.
        envoyer(post(route(cours, "/chapitres/" + chapitreEtranger + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());
        envoyer(delete(route(cours, "/chapitres/" + chapitreEtranger + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());
        envoyer(post(route(cours, "/chapitres/999999/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());
        // Cours inexistant : 404.
        envoyer(post(route(999_999, "/chapitres/" + chapitre + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());
        // Inscrit à un seul cours : terminer un chapitre de l'autre cours reste refusé (pas inscrit là-bas).
        envoyer(post(route(autreCours, "/chapitres/" + chapitreEtranger + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isForbidden());
        assertThat(termineRepository.count()).isZero();
    }

    @Test
    void coursRepasseEnBrouillonNeSuitPlusMaisLInscriptionResteLisible() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        long chapitre = chapitres(cours).get(0);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(patch(route(cours, "/statut")), FORMATEUR_A, "ENSEIGNANT", "{\"statut\":\"BROUILLON\"}");

        envoyer(post(route(cours, "/chapitres/" + chapitre + "/terminer")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());
        envoyer(get("/api/cours/mes-inscriptions"), APPRENANT, "ETUDIANT").andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void maProgressionDonneLesChapitresTerminesPourLeLecteur() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 3);
        List<Long> c = chapitres(cours);

        // Pas inscrit : 200 sans data (ce n'est pas une erreur, le lecteur propose de s'inscrire).
        envoyer(get(route(cours, "/progression")), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").doesNotExist());

        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/chapitres/" + c.get(2) + "/terminer")), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data.chapitresTermines.length()").value(1))
                .andExpect(jsonPath("$.data.chapitresTermines[0]").value(c.get(2)));
        envoyer(post(route(cours, "/chapitres/" + c.get(0) + "/terminer")), APPRENANT, "ETUDIANT");

        envoyer(get(route(cours, "/progression")), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data.progression").value(0.6667))
                .andExpect(jsonPath("$.data.chapitresTermines.length()").value(2))
                .andExpect(jsonPath("$.data.tempsPasseMinutes").value(0));
        // Réservée à l'apprenant lui-même ; le contrat 7, lui, n'expose pas la liste des chapitres.
        envoyer(get(route(cours, "/progression")), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isForbidden());
        envoyer(get(route(cours, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT").andExpect(jsonPath("$.data[0].chapitresTermines").doesNotExist());
        envoyer(get(route(999_999, "/progression")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ contrat 7

    @Test
    void contrat7ParEtudiant() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 2);
        long autre = coursPublie(FORMATEUR_B, 1);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/chapitres/" + chapitres(cours).get(0) + "/terminer")), APPRENANT, "ETUDIANT");
        envoyer(post(route(autre, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/inscriptions")), AUTRE_APPRENANT, "ETUDIANT");

        // etudiantId absent : 400.
        envoyer(get("/api/cours/inscriptions"), APPRENANT, "ETUDIANT").andExpect(status().isBadRequest());
        envoyer(get("/api/cours/inscriptions"), ADMIN, "ADMIN").andExpect(status().isBadRequest());

        // L'apprenant lui-même.
        envoyer(get("/api/cours/inscriptions?etudiantId=" + APPRENANT), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].etudiantId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is((int) APPRENANT))))
                .andExpect(jsonPath("$.data[?(@.coursId == " + cours + ")].progression").value(0.5))
                .andExpect(jsonPath("$.data[0].tempsPasseMinutes").value(0));
        // Un apprenant ne lit pas celles d'un autre : 403.
        envoyer(get("/api/cours/inscriptions?etudiantId=" + AUTRE_APPRENANT), APPRENANT, "ETUDIANT").andExpect(status().isForbidden());
        // Tout ENSEIGNANT (y compris sans lien avec les cours) et l'ADMIN peuvent lire, à la lettre du contrat.
        envoyer(get("/api/cours/inscriptions?etudiantId=" + APPRENANT), FORMATEUR_B, "ENSEIGNANT")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        envoyer(get("/api/cours/inscriptions?etudiantId=" + APPRENANT), ADMIN, "ADMIN")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        // Apprenant sans inscription : liste vide, pas d'erreur.
        envoyer(get("/api/cours/inscriptions?etudiantId=777"), ADMIN, "ADMIN").andExpect(jsonPath("$.data.length()").value(0));
        // Identifiant illisible : 400.
        envoyer(get("/api/cours/inscriptions?etudiantId=abc"), ADMIN, "ADMIN").andExpect(status().isBadRequest());
    }

    @Test
    void contrat7ParCours() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 2);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/inscriptions")), AUTRE_APPRENANT, "ETUDIANT");

        envoyer(get(route(cours, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].etudiantId").value(APPRENANT))
                .andExpect(jsonPath("$.data[0].coursId").value(cours))
                .andExpect(jsonPath("$.data[0].progression").value(0.0))
                .andExpect(jsonPath("$.data[0].termine").value(false))
                .andExpect(jsonPath("$.data[0].dateInscription").exists())
                .andExpect(jsonPath("$.data[0].tempsPasseMinutes").value(0))
                .andExpect(jsonPath("$.data[0].dateDerniereActivite").doesNotExist());
        envoyer(get(route(cours, "/inscriptions")), ADMIN, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        // Un autre formateur : 403 ; un apprenant, même inscrit : 403 ; cours inexistant : 404.
        envoyer(get(route(cours, "/inscriptions")), FORMATEUR_B, "ENSEIGNANT").andExpect(status().isForbidden());
        envoyer(get(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT").andExpect(status().isForbidden());
        envoyer(get(route(999_999, "/inscriptions")), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isNotFound());
        envoyer(get(route(999_999, "/inscriptions")), ADMIN, "ADMIN").andExpect(status().isNotFound());
    }

    @Test
    void mesInscriptionsNeRenvoieQueCellesDeLUtilisateur() throws Exception {
        long premier = coursPublie(FORMATEUR_A, 1);
        long second = coursPublie(FORMATEUR_B, 1);
        envoyer(post(route(premier, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(second, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(premier, "/inscriptions")), AUTRE_APPRENANT, "ETUDIANT");

        envoyer(get("/api/cours/mes-inscriptions"), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].etudiantId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is((int) APPRENANT))));
        envoyer(get("/api/cours/mes-inscriptions"), FORMATEUR_A, "ENSEIGNANT").andExpect(jsonPath("$.data.length()").value(0));
    }

    // ------------------------------------------------------------------ nbInscrits et nettoyage

    @Test
    void nbInscritsAjouteAuxReponsesDesCours() throws Exception {
        long a = coursPublie(FORMATEUR_A, 1);
        long b = coursPublie(FORMATEUR_A, 1);
        envoyer(get("/api/cours/" + a), APPRENANT, "ETUDIANT").andExpect(jsonPath("$.data.nbInscrits").value(0));

        envoyer(post(route(a, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(a, "/inscriptions")), AUTRE_APPRENANT, "ETUDIANT");
        envoyer(post(route(b, "/inscriptions")), APPRENANT, "ETUDIANT");

        envoyer(get("/api/cours/" + a), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data.nbInscrits").value(2))
                // Les champs existants restent tels quels (le Quiz, le Forum et le User les utilisent).
                .andExpect(jsonPath("$.data.id").value(a))
                .andExpect(jsonPath("$.data.titre").exists())
                .andExpect(jsonPath("$.data.enseignantId").value(FORMATEUR_A))
                .andExpect(jsonPath("$.data.nbChapitres").value(1));
        envoyer(get("/api/cours"), FORMATEUR_A, "ENSEIGNANT")
                .andExpect(jsonPath("$.data[?(@.id == " + a + ")].nbInscrits").value(2))
                .andExpect(jsonPath("$.data[?(@.id == " + b + ")].nbInscrits").value(1));
    }

    @Test
    void supprimerLeCoursSupprimeSesInscriptionsEtChapitresTermines() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 2);
        long conserve = coursPublie(FORMATEUR_B, 1);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/chapitres/" + chapitres(cours).get(0) + "/terminer")), APPRENANT, "ETUDIANT");
        envoyer(post(route(conserve, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(conserve, "/chapitres/" + chapitres(conserve).get(0) + "/terminer")), APPRENANT, "ETUDIANT");

        envoyer(delete("/api/cours/" + cours), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isOk());

        assertThat(inscriptionRepository.findAll()).extracting("coursId").containsExactly(conserve);
        assertThat(termineRepository.count()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ lot 2 : temps passé

    @Test
    void signalDActivite_compteUneMinuteEtDateLActivite() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");

        envoyer(post(route(cours, "/activite")), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tempsPasseMinutes").value(1))
                .andExpect(jsonPath("$.data.dateDerniereActivite").exists());
    }

    @Test
    void signauxTropRapproches_uneSeuleMinute_puisUneDePlusApres55Secondes() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/activite")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/activite")), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data.tempsPasseMinutes").value(1)); // deuxième onglet ou double envoi : refusé

        jdbc.update("update inscription set dernier_signal = ?", java.time.LocalDateTime.now().minusSeconds(56));
        envoyer(post(route(cours, "/activite")), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data.tempsPasseMinutes").value(2));
    }

    @Test
    void plafondDe600MinutesParJour() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        jdbc.update("update inscription set temps_passe_minutes = 600, minutes_du_jour = 600, jour_activite = ?, dernier_signal = ?",
                java.time.LocalDate.now(), java.time.LocalDateTime.now().minusMinutes(5));

        envoyer(post(route(cours, "/activite")), APPRENANT, "ETUDIANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tempsPasseMinutes").value(600));
    }

    @Test
    void ancienneInscriptionSansTemps_passeA1AuPremierSignal() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        jdbc.update("update inscription set temps_passe_minutes = null");

        envoyer(post(route(cours, "/activite")), APPRENANT, "ETUDIANT")
                .andExpect(jsonPath("$.data.tempsPasseMinutes").value(1));
    }

    @Test
    void signalDActivite_apprenantInscritACoursPublieSeulement() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 1);
        envoyer(post(route(cours, "/activite")), APPRENANT, "ETUDIANT").andExpect(status().isForbidden()); // pas inscrit
        envoyer(post(route(cours, "/activite")), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isForbidden());
        envoyer(post(route(999_999, "/activite")), APPRENANT, "ETUDIANT").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ contrat 8

    @Test
    void contrat8_toutesLesInscriptions_adminSeulement() throws Exception {
        long cours = coursPublie(FORMATEUR_A, 2);
        envoyer(post(route(cours, "/inscriptions")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/chapitres/" + chapitres(cours).get(0) + "/terminer")), APPRENANT, "ETUDIANT");
        envoyer(post(route(cours, "/inscriptions")), AUTRE_APPRENANT, "ETUDIANT");

        envoyer(get("/api/cours/inscriptions/toutes"), ADMIN, "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].etudiantId").value(APPRENANT))
                .andExpect(jsonPath("$.data[0].coursId").value(cours))
                .andExpect(jsonPath("$.data[0].progression").value(0.5))
                .andExpect(jsonPath("$.data[0].termine").value(false))
                .andExpect(jsonPath("$.data[0].dateInscription").doesNotExist());
        envoyer(get("/api/cours/inscriptions/toutes"), FORMATEUR_A, "ENSEIGNANT").andExpect(status().isForbidden());
        envoyer(get("/api/cours/inscriptions/toutes"), APPRENANT, "ETUDIANT").andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ outils

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

    private long coursBrouillon(long formateur, int chapitres) throws Exception {
        String json = "{\"titre\":\"Cours\",\"description\":\"Description\",\"categorie\":\"Informatique\",\"niveau\":\"DEBUTANT\"}";
        long id = ((Number) JsonPath.read(corps(envoyer(post("/api/cours"), formateur, "ENSEIGNANT", json).andExpect(status().isCreated())),
                "$.data.id")).longValue();
        for (int i = 1; i <= chapitres; i++) {
            envoyer(post(route(id, "/chapitres")), formateur, "ENSEIGNANT", "{\"titre\":\"Chapitre " + i + "\",\"dureeMinutes\":10}")
                    .andExpect(status().isCreated());
        }
        return id;
    }

    private long coursPublie(long formateur, int chapitres) throws Exception {
        long id = coursBrouillon(formateur, chapitres);
        envoyer(patch(route(id, "/statut")), formateur, "ENSEIGNANT", "{\"statut\":\"PUBLIE\"}").andExpect(status().isOk());
        return id;
    }

    /** Identifiants des chapitres du cours, dans l'ordre. */
    private List<Long> chapitres(long coursId) throws Exception {
        List<Number> ids = JsonPath.read(corps(envoyer(get(route(coursId, "/chapitres")), ADMIN, "ADMIN")), "$.data[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}

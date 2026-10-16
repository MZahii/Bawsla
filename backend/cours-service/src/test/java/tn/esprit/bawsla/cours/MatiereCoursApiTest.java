package tn.esprit.bawsla.cours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.StatutCours;
import tn.esprit.bawsla.cours.repository.AudioRevisionRepository;
import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.ExplicationAudioRepository;
import tn.esprit.bawsla.cours.repository.FicheRevisionRepository;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;
import tn.esprit.bawsla.cours.repository.ResumeLocalRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;
import tn.esprit.bawsla.cours.service.MigrationMatieres;

/**
 * Matières des cours, compatibles avec le module Quiz : matière obligatoire dans la liste commune, précision pour « Autre »,
 * comparaison stricte après normalisation Unicode NFC, filtre du catalogue et migration des anciennes catégories.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cours_matieres;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatiereCoursApiTest {

    private static final long FORMATEUR = 10L;
    private static final long APPRENANT = 30L;

    @Autowired MockMvc mvc;
    @Autowired CoursRepository coursRepository;
    @Autowired MigrationMatieres migration;
    @Autowired TraitementIARepository traitementRepository;
    @Autowired ResumeLocalRepository resumeRepository;
    @Autowired ExplicationAudioRepository explicationRepository;
    @Autowired AudioRevisionRepository audioRepository;
    @Autowired FicheRevisionRepository ficheRepository;
    @Autowired PageDocumentRepository pageRepository;

    @BeforeEach
    void nettoyer() {
        traitementRepository.deleteAll();
        resumeRepository.deleteAll();
        explicationRepository.deleteAll();
        audioRepository.deleteAll();
        ficheRepository.deleteAll();
        pageRepository.deleteAll();
        coursRepository.deleteAll();
    }

    // ------------------------------------------------------------------ validation à la création

    @Test
    void matiereValideEstEnregistreeSansPrecision() throws Exception {
        creer("Algèbre", "Mathématiques", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.categorie").value("Mathématiques"))
                .andExpect(jsonPath("$.data.categoriePrecision").doesNotExist());
    }

    @Test
    void les_douze_matieres_sont_acceptees() throws Exception {
        for (String matiere : tn.esprit.bawsla.cours.service.Matieres.LISTE) {
            String precision = "Autre".equals(matiere) ? "Cryptographie" : null;
            creer("Cours " + matiere, matiere, precision).andExpect(status().isCreated());
        }
        assertThat(coursRepository.count()).isEqualTo(12);
    }

    @Test
    void matiereInconnueEst400AvecLeMessageSurLeChamp() throws Exception {
        creer("Cours", "Cuisine", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.categorie").value("Matière inconnue : choisis-en une dans la liste"));
        // Les anciennes catégories ne sont plus acceptées en création.
        creer("Cours", "Données", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.categorie").exists());
    }

    @Test
    void matiereObligatoire() throws Exception {
        creerBrut("{\"titre\":\"Sans matière\",\"niveau\":\"DEBUTANT\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.categorie").value("La matière est obligatoire"));
        creer("Cours", "   ", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.categorie").value("La matière est obligatoire"));
        assertThat(coursRepository.count()).isZero();
    }

    @Test
    void autreSansPrecisionEst400() throws Exception {
        creer("Cours", "Autre", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.categoriePrecision").value(containsString("2 à 80 caractères")));
        creer("Cours", "Autre", "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.categoriePrecision").exists());
    }

    @Test
    void precisionTropCourteOuTropLongue() throws Exception {
        creer("Cours", "Autre", "X").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.categoriePrecision").exists());
        creer("Cours", "Autre", "a".repeat(81)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.categoriePrecision").exists());
        creer("Cours", "Autre", "ab").andExpect(status().isCreated());
        creer("Cours 2", "Autre", "a".repeat(80)).andExpect(status().isCreated());
    }

    @Test
    void precisionSansAutreEst400() throws Exception {
        creer("Cours", "Informatique", "Réseaux")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.categoriePrecision").value("La précision n'est acceptée que pour la matière « Autre »"));
    }

    @Test
    void autreAvecPrecisionEstRenvoyee() throws Exception {
        creer("Cours", "Autre", "  Cryptographie  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.categorie").value("Autre"))
                .andExpect(jsonPath("$.data.categoriePrecision").value("Cryptographie"));
    }

    // ------------------------------------------------------------------ modification

    @Test
    void modificationValideLesMemesRegles() throws Exception {
        long id = identifiant(creer("Cours", "Autre", "Cryptographie").andExpect(status().isCreated()));
        modifier(id, "Autre", null).andExpect(status().isBadRequest());
        modifier(id, "Cuisine", null).andExpect(status().isBadRequest());
        modifier(id, "Histoire", "Antiquité").andExpect(status().isBadRequest());
        // Changer de matière efface la précision.
        modifier(id, "Histoire", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categorie").value("Histoire")).andExpect(jsonPath("$.data.categoriePrecision").doesNotExist());
        assertThat(coursRepository.findById(id).orElseThrow().getCategoriePrecision()).isNull();
        modifier(id, "Autre", "Cryptographie appliquée").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoriePrecision").value("Cryptographie appliquée"));
    }

    // ------------------------------------------------------------------ comparaison stricte, forme NFC

    @Test
    void formeUnicodeDecomposeeEstNormaliseeEnNfc() throws Exception {
        String decomposee = "Mathématiques"; // e + accent aigu combinant
        assertThat(decomposee).isNotEqualTo("Mathématiques");
        assertThat(Normalizer.normalize(decomposee, Normalizer.Form.NFC)).isEqualTo("Mathématiques");

        long id = identifiant(creer("Cours", decomposee, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.categorie").value("Mathématiques")));
        assertThat(coursRepository.findById(id).orElseThrow().getCategorie()).isEqualTo("Mathématiques").isNotEqualTo(decomposee);
        // Espaces autour supprimés, y compris avec une matière décomposée à la modification.
        modifier(id, "  Géographie  ", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.categorie").value("Géographie"));
    }

    @Test
    void comparaisonStricteSurLaCasseEtLesAccents() throws Exception {
        creer("Cours", "mathématiques", null).andExpect(status().isBadRequest());
        creer("Cours", "MATHÉMATIQUES", null).andExpect(status().isBadRequest());
        creer("Cours", "Mathematiques", null).andExpect(status().isBadRequest());
        creer("Cours", "Economie et gestion", null).andExpect(status().isBadRequest());
        creer("Cours", "Économie et gestion", null).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ filtre du catalogue

    @Test
    void filtreParMatiereAvecLesNouveauxLibelles() throws Exception {
        publier(identifiant(creer("Algèbre", "Mathématiques", null)));
        publier(identifiant(creer("Sécurité", "Autre", "Cryptographie")));
        publier(identifiant(creer("Java", "Informatique", null)));

        mvc.perform(get("/api/cours").param("categorie", "Mathématiques").header("X-User-Id", APPRENANT).header("X-User-Role", "ETUDIANT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].titre").value("Algèbre"));
        // Le filtre accepte aussi la forme décomposée.
        mvc.perform(get("/api/cours").param("categorie", "Mathématiques").header("X-User-Id", APPRENANT).header("X-User-Role", "ETUDIANT"))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(get("/api/cours").param("categorie", "Autre").header("X-User-Id", APPRENANT).header("X-User-Role", "ETUDIANT"))
                .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].categoriePrecision").value("Cryptographie"));
        mvc.perform(get("/api/cours").header("X-User-Id", APPRENANT).header("X-User-Role", "ETUDIANT"))
                .andExpect(jsonPath("$.data", hasSize(3)));
        mvc.perform(get("/api/cours").param("categorie", "Histoire").header("X-User-Id", APPRENANT).header("X-User-Role", "ETUDIANT"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ------------------------------------------------------------------ migration au démarrage

    @Test
    void migrationDesCoursExistants() {
        for (String ancien : new String[] {"Données", "Back-end", "Front-end", "DevOps", "Outils"}) {
            enregistrer("Ancien " + ancien, ancien);
        }
        enregistrer("Libellé libre", "Cryptographie");
        enregistrer("Libellé très long", "Introduction générale aux systèmes distribués, à la tolérance aux pannes et à la cohérence");
        enregistrer("Libellé minuscule", "X");
        enregistrer("Forme décomposée", "Géographie");
        enregistrer("Déjà bon", "Histoire");
        enregistrer("Sans matière", null);

        MigrationMatieres.Bilan bilan = migration.migrer();

        assertThat(bilan.versInformatique()).isEqualTo(5);
        assertThat(bilan.versAutre()).isEqualTo(3);
        assertThat(bilan.normalisees()).isEqualTo(1);
        assertThat(bilan.sansMatiere()).isEqualTo(1);
        assertThat(coursRepository.findAll()).filteredOn(c -> c.getTitre().startsWith("Ancien")).hasSize(5)
                .allSatisfy(c -> assertThat(c.getCategorie()).isEqualTo("Informatique"));
        assertThat(matiere("Libellé libre")).containsExactly("Autre", "Cryptographie");
        String longue = matiere("Libellé très long")[1];
        assertThat(matiere("Libellé très long")[0]).isEqualTo("Autre");
        assertThat(longue.length()).isBetween(2, 80);
        assertThat(longue).startsWith("Introduction générale aux systèmes distribués").doesNotEndWith(" ");
        assertThat(matiere("Libellé minuscule")).containsExactly("Autre", "Non précisée");
        assertThat(matiere("Forme décomposée")[0]).isEqualTo("Géographie");
        assertThat(matiere("Déjà bon")[0]).isEqualTo("Histoire");
        assertThat(matiere("Sans matière")[0]).isNull();

        // Idempotent : un second passage ne change plus rien.
        MigrationMatieres.Bilan second = migration.migrer();
        assertThat(second.total()).isZero();
        assertThat(matiere("Libellé libre")).containsExactly("Autre", "Cryptographie");
    }

    @Test
    void lesCoursMigresRestentModifiablesAvecLesNouvellesRegles() throws Exception {
        enregistrer("Ancien cours", "Back-end");
        migration.migrer();
        long id = coursRepository.findAll().get(0).getId();
        modifier(id, "Informatique", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.categorie").value("Informatique"));
    }

    // ------------------------------------------------------------------ aides

    private ResultActions creer(String titre, String categorie, String precision) throws Exception {
        return creerBrut(json(titre, categorie, precision));
    }

    private ResultActions creerBrut(String corps) throws Exception {
        return mvc.perform(post("/api/cours").header("X-User-Id", FORMATEUR).header("X-User-Role", "ENSEIGNANT")
                .contentType(MediaType.APPLICATION_JSON).content(corps.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions modifier(long id, String categorie, String precision) throws Exception {
        return mvc.perform(put("/api/cours/" + id).header("X-User-Id", FORMATEUR).header("X-User-Role", "ENSEIGNANT")
                .contentType(MediaType.APPLICATION_JSON).content(json("Cours modifié", categorie, precision).getBytes(StandardCharsets.UTF_8)));
    }

    private static String json(String titre, String categorie, String precision) {
        StringBuilder sb = new StringBuilder("{\"titre\":\"").append(titre).append("\",\"niveau\":\"DEBUTANT\"");
        if (categorie != null) {
            sb.append(",\"categorie\":\"").append(categorie).append("\"");
        }
        if (precision != null) {
            sb.append(",\"categoriePrecision\":\"").append(precision).append("\"");
        }
        return sb.append("}").toString();
    }

    private void publier(long id) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/cours/" + id + "/statut")
                        .header("X-User-Id", FORMATEUR).header("X-User-Role", "ENSEIGNANT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"PUBLIE\"}"))
                .andExpect(status().isBadRequest()); // publier exige au moins un chapitre : on en ajoute un
        mvc.perform(post("/api/cours/" + id + "/chapitres").header("X-User-Id", FORMATEUR).header("X-User-Role", "ENSEIGNANT")
                .contentType(MediaType.APPLICATION_JSON).content("{\"titre\":\"Intro\",\"contenu\":\"Texte\",\"dureeMinutes\":10}")).andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/cours/" + id + "/statut")
                        .header("X-User-Id", FORMATEUR).header("X-User-Role", "ENSEIGNANT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"PUBLIE\"}"))
                .andExpect(status().isOk());
    }

    private void enregistrer(String titre, String categorie) {
        Cours c = new Cours();
        c.setTitre(titre);
        c.setCategorie(categorie);
        c.setEnseignantId(FORMATEUR);
        c.setStatut(StatutCours.PUBLIE);
        coursRepository.save(c);
    }

    /** {matière, précision} du cours de ce titre, relus en base. */
    private String[] matiere(String titre) {
        Cours c = coursRepository.findAll().stream().filter(x -> titre.equals(x.getTitre())).findFirst().orElseThrow();
        return new String[] {c.getCategorie(), c.getCategoriePrecision()};
    }

    private static long identifiant(ResultActions actions) throws Exception {
        Number n = JsonPath.read(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
        return n.longValue();
    }
}

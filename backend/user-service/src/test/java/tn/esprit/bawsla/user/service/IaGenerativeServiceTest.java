package tn.esprit.bawsla.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import tn.esprit.bawsla.user.client.AiClient;
import tn.esprit.bawsla.user.client.CoursClient;
import tn.esprit.bawsla.user.client.CoursDto;
import tn.esprit.bawsla.user.client.IaGenerativeDto;
import tn.esprit.bawsla.user.client.InscriptionDto;
import tn.esprit.bawsla.user.client.NotionRateeDto;
import tn.esprit.bawsla.user.client.ProfilIaDto;
import tn.esprit.bawsla.user.client.QuizClient;
import tn.esprit.bawsla.user.client.RecommandationsIaDto;
import tn.esprit.bawsla.user.client.RisqueIaDto;
import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ForbiddenException;
import tn.esprit.bawsla.user.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.ApprenantsResponse;
import tn.esprit.bawsla.user.dto.ApprenantsResponse.CoursResume;
import tn.esprit.bawsla.user.dto.ApprenantsResponse.Ligne;
import tn.esprit.bawsla.user.dto.ContenuIaResponse;
import tn.esprit.bawsla.user.dto.IaApprenantResponse;
import tn.esprit.bawsla.user.dto.IndicateursResponse.Indicateurs;
import tn.esprit.bawsla.user.entity.ContenuIa;
import tn.esprit.bawsla.user.entity.Parcours;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.TypeContenuIa;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.ContenuIaRepository;
import tn.esprit.bawsla.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class IaGenerativeServiceTest {

    private static final String JETON = "Bearer abc";
    private static final CurrentUser APPRENANT = new CurrentUser(5L, CurrentUser.ETUDIANT);
    private static final CurrentUser FORMATEUR = new CurrentUser(2L, CurrentUser.ENSEIGNANT);
    private static final Indicateurs IND = new Indicateurs(0.2, 0.2, 1, 4, 4, 0.33, 2, 1, 0, 1, 0);

    @Mock private IaApprenantService iaApprenantService;
    @Mock private ApprenantsService apprenantsService;
    @Mock private QuizClient quizClient;
    @Mock private CoursClient coursClient;
    @Mock private AiClient aiClient;
    @Mock private UserRepository userRepository;
    @Mock private ContenuIaRepository contenuRepository;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private IaGenerativeService service;

    @BeforeEach
    void setUp() {
        service = new IaGenerativeService(iaApprenantService, apprenantsService, quizClient, coursClient, aiClient,
                userRepository, contenuRepository, mapper);
        User lina = new User();
        lina.setId(5L);
        lina.setNom("Trabelsi");
        lina.setPrenom("Lina");
        lina.setEmail("lina@example.com");
        lina.setRole(Role.ETUDIANT);
        Parcours p = new Parcours();
        p.setObjectif("Trouver un premier emploi");
        p.setTemps("1 à 3 h");
        lina.setParcours(p);
        lenient().when(userRepository.findById(5L)).thenReturn(Optional.of(lina));
        lenient().when(quizClient.notionsRatees(any())).thenReturn(ApiResponse.ok(List.of(
                new NotionRateeDto("Jointures", 10L, 2, 0.3), new NotionRateeDto("Index", 10L, 1, 0.5))));
        lenient().when(coursClient.catalogue()).thenReturn(ApiResponse.ok(List.of(
                new CoursDto(10L, "SQL pour débuter", "Informatique", "DEBUTANT", "PUBLIE", 2L),
                new CoursDto(11L, "SQL avancé", "Informatique", "INTERMEDIAIRE", "PUBLIE", 2L))));
        lenient().when(coursClient.inscriptions(5L)).thenReturn(ApiResponse.ok(List.of(
                new InscriptionDto(5L, 10L, null, 0.33, 2, false, null))));
        lenient().when(contenuRepository.findByUserIdAndTypeAndCible(any(), any(), any())).thenReturn(Optional.empty());
    }

    private static IaApprenantResponse iaComplete() {
        return new IaApprenantResponse(new ProfilIaDto.Groupe(3, "Décrocheur", "…"), null, null, null,
                List.of(new RecommandationsIaDto.CoursRecommande(11L, "SQL avancé", 0.6, "Domaine")), null, true,
                Map.of(), true, IND);
    }

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    // ------------------------------------------------------------------ conseil

    @Test
    void conseil_envoieDesChiffresEtDesTitres_jamaisLeNomNiLEmail() throws Exception {
        when(iaApprenantService.pourMoi(APPRENANT, JETON)).thenReturn(iaComplete());
        when(aiClient.conseil(eq(JETON), any())).thenReturn(ApiResponse.ok(json(
                "{\"conseil\":\"Bravo pour ton premier quiz.\",\"actions\":[\"Revois les jointures.\"],\"fournisseur\":\"gemini\"}")));

        ContenuIaResponse r = service.conseil(APPRENANT, JETON);

        assertThat(r.depuisCache()).isFalse();
        assertThat(r.fournisseur()).isEqualTo("gemini");
        assertThat(r.contenu().path("conseil").asText()).startsWith("Bravo");
        ArgumentCaptor<IaGenerativeDto.Conseil> requete = ArgumentCaptor.forClass(IaGenerativeDto.Conseil.class);
        verify(aiClient).conseil(eq(JETON), requete.capture());
        assertThat(requete.getValue().profil()).isEqualTo("Décrocheur");
        assertThat(requete.getValue().coursSuivis()).containsExactly("SQL pour débuter");
        assertThat(requete.getValue().notionsARevoir()).containsExactly("Jointures", "Index");
        assertThat(requete.getValue().coursRecommandes()).containsExactly("SQL avancé");
        String envoye = mapper.writeValueAsString(requete.getValue());
        assertThat(envoye).doesNotContain("Lina", "Trabelsi", "lina@example.com", "etudiantId", "\"id\"");
        verify(contenuRepository).save(any(ContenuIa.class));
    }

    @Test
    void conseil_memesDonnees_texteGardeSansNouvelAppelAuLlm() throws Exception {
        when(iaApprenantService.pourMoi(APPRENANT, JETON)).thenReturn(iaComplete());
        AtomicReference<ContenuIa> garde = new AtomicReference<>();
        when(contenuRepository.findByUserIdAndTypeAndCible(5L, TypeContenuIa.CONSEIL, 0L))
                .thenAnswer(inv -> Optional.ofNullable(garde.get()));
        when(contenuRepository.save(any(ContenuIa.class))).thenAnswer(inv -> {
            garde.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(aiClient.conseil(eq(JETON), any())).thenReturn(ApiResponse.ok(json("{\"conseil\":\"A\",\"actions\":[\"B\"]}")));

        service.conseil(APPRENANT, JETON);
        ContenuIaResponse deuxieme = service.conseil(APPRENANT, JETON);

        assertThat(deuxieme.depuisCache()).isTrue();
        assertThat(deuxieme.contenu().path("conseil").asText()).isEqualTo("A");
        verify(aiClient, times(1)).conseil(anyString(), any());
    }

    @Test
    void conseil_donneesChangees_nouvelleGeneration() throws Exception {
        when(iaApprenantService.pourMoi(APPRENANT, JETON)).thenReturn(iaComplete());
        ContenuIa ancien = new ContenuIa(5L, TypeContenuIa.CONSEIL, 0L);
        ancien.remplacer("empreinte-d-avant", "{\"conseil\":\"Ancien\"}", "gemini", LocalDateTime.now().minusDays(1));
        when(contenuRepository.findByUserIdAndTypeAndCible(5L, TypeContenuIa.CONSEIL, 0L)).thenReturn(Optional.of(ancien));
        when(aiClient.conseil(eq(JETON), any())).thenReturn(ApiResponse.ok(json("{\"conseil\":\"Nouveau\",\"actions\":[\"B\"]}")));

        ContenuIaResponse r = service.conseil(APPRENANT, JETON);

        assertThat(r.depuisCache()).isFalse();
        assertThat(ancien.getContenu()).contains("Nouveau");
    }

    @Test
    void conseil_donneesIncompletes_400SansAppelAuLlm() {
        when(iaApprenantService.pourMoi(APPRENANT, JETON)).thenReturn(new IaApprenantResponse(null,
                "Pas encore assez de données : le temps passé…", null, null, List.of(), null, null, Map.of(), false, null));

        assertThatThrownBy(() -> service.conseil(APPRENANT, JETON))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("temps passé");
        verify(aiClient, never()).conseil(anyString(), any());
    }

    @Test
    void llmIndisponible_503AvecUnMessageClair() {
        when(iaApprenantService.pourMoi(APPRENANT, JETON)).thenReturn(iaComplete());
        when(aiClient.conseil(eq(JETON), any())).thenThrow(new ServiceUnavailableException("Quota atteint"));

        assertThatThrownBy(() -> service.conseil(APPRENANT, JETON))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage(IaGenerativeService.INDISPONIBLE);
        verify(contenuRepository, never()).save(any());
    }

    @Test
    void conseilEtPlan_reservesALApprenant() {
        assertThatThrownBy(() -> service.conseil(FORMATEUR, JETON)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.plan(FORMATEUR, JETON)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.dernier(FORMATEUR, TypeContenuIa.CONSEIL, null)).isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------------ plan

    @Test
    void plan_envoieLeTempsDisponibleEtLesCoursEnCours() throws Exception {
        when(iaApprenantService.pourMoi(APPRENANT, JETON)).thenReturn(iaComplete());
        when(aiClient.plan(eq(JETON), any())).thenReturn(ApiResponse.ok(json("{\"resume\":\"Semaine SQL\",\"seances\":[]}")));

        service.plan(APPRENANT, JETON);

        ArgumentCaptor<IaGenerativeDto.Plan> requete = ArgumentCaptor.forClass(IaGenerativeDto.Plan.class);
        verify(aiClient).plan(eq(JETON), requete.capture());
        assertThat(requete.getValue().tempsDisponible()).isEqualTo("1 à 3 h");
        assertThat(requete.getValue().coursEnCours()).containsExactly(new IaGenerativeDto.CoursEnCours("SQL pour débuter", 0.33));
    }

    // ------------------------------------------------------------------ explication du risque

    @Test
    void explication_formateur_gardeeParApprenant() throws Exception {
        IaApprenantResponse ia = new IaApprenantResponse(new ProfilIaDto.Groupe(3, "Décrocheur", "…"), null,
                new RisqueIaDto.Resultat(0.72, "eleve", List.of("Note moyenne basse aux quiz (20%)")), null, null, null,
                true, Map.of(), true, IND);
        when(iaApprenantService.pourUnApprenant(FORMATEUR, 5L, JETON)).thenReturn(ia);
        when(aiClient.explicationRisque(eq(JETON), any())).thenReturn(ApiResponse.ok(json(
                "{\"resume\":\"Risque élevé\",\"actions\":[\"Relance\"],\"messageSuggere\":\"Courage !\"}")));

        service.explication(FORMATEUR, 5L, JETON);

        ArgumentCaptor<ContenuIa> garde = ArgumentCaptor.forClass(ContenuIa.class);
        verify(contenuRepository).save(garde.capture());
        assertThat(garde.getValue().getUserId()).isEqualTo(2L);
        assertThat(garde.getValue().getCible()).isEqualTo(5L);
        ArgumentCaptor<IaGenerativeDto.ExplicationRisque> requete = ArgumentCaptor.forClass(IaGenerativeDto.ExplicationRisque.class);
        verify(aiClient).explicationRisque(eq(JETON), requete.capture());
        assertThat(requete.getValue().niveau()).isEqualTo("eleve");
        assertThat(requete.getValue().notionsARevoir()).containsExactly("Jointures", "Index");
    }

    @Test
    void explication_refuseeALApprenant() {
        assertThatThrownBy(() -> service.explication(APPRENANT, 5L, JETON)).isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------------ rapport de la classe

    @Test
    void rapport_statistiquesAgregees_unApprenantCompteUneFois() {
        RisqueIaDto.Resultat eleve = new RisqueIaDto.Resultat(0.72, "eleve", List.of());
        List<Ligne> lignes = List.of(
                new Ligne(5L, "Trabelsi", "Lina", null, 10L, "SQL pour débuter", null, 0.33, 2, null, eleve, null, "Décrocheur"),
                new Ligne(5L, "Trabelsi", "Lina", null, 11L, "SQL avancé", null, 0.0, 0, null, eleve, null, "Décrocheur"),
                new Ligne(6L, "Ben Ali", "Sami", null, 10L, "SQL pour débuter", null, 1.0, 7, null, null, "…", null));
        ApprenantsResponse a = new ApprenantsResponse(List.of(new CoursResume(10L, "SQL pour débuter"),
                new CoursResume(11L, "SQL avancé")), lignes, null, null, true);

        IaGenerativeDto.Cohorte c = service.cohorte(a);

        assertThat(c.nbApprenants()).isEqualTo(2);
        assertThat(c.nbCours()).isEqualTo(2);
        assertThat(c.risques()).isEqualTo(new IaGenerativeDto.RepartitionRisque(0, 0, 1, 1));
        assertThat(c.progressionMoyenne()).isEqualTo(0.44);
        assertThat(c.profils()).containsExactly(new IaGenerativeDto.Effectif("Décrocheur", 1));
        assertThat(c.notions()).containsExactly(new IaGenerativeDto.Effectif("Jointures", 2), new IaGenerativeDto.Effectif("Index", 2));
        assertThat(c.cours().get(0)).isEqualTo(new IaGenerativeDto.CoursStat("SQL pour débuter", 2, 0.67));
        assertThat(c.toString()).doesNotContain("Lina", "Trabelsi", "Sami");
    }

    @Test
    void rapport_sansInscrit_400SansAppelAuLlm() {
        when(apprenantsService.pourFormateur(FORMATEUR, JETON)).thenReturn(new ApprenantsResponse(
                List.of(new CoursResume(10L, "SQL")), List.of(), null, null, null));

        assertThatThrownBy(() -> service.rapport(FORMATEUR, JETON))
                .isInstanceOf(BadRequestException.class).hasMessage(IaGenerativeService.SANS_INSCRIT);
        verify(aiClient, never()).rapportCohorte(anyString(), any());
    }

    // ------------------------------------------------------------------ domaines depuis une phrase

    @Test
    void suggestion_envoieLesListesAutorisees() throws Exception {
        when(aiClient.domaines(eq(JETON), any())).thenReturn(ApiResponse.ok(json("{\"domaines\":[\"Informatique\"]}")));

        JsonNode r = service.suggestionParcours(APPRENANT, "Je veux devenir data analyst", JETON);

        assertThat(r.path("domaines").get(0).asText()).isEqualTo("Informatique");
        ArgumentCaptor<IaGenerativeDto.Domaines> requete = ArgumentCaptor.forClass(IaGenerativeDto.Domaines.class);
        verify(aiClient).domaines(eq(JETON), requete.capture());
        assertThat(requete.getValue().domainesDisponibles()).contains("Informatique", "Mathématiques").doesNotContain("Autre");
        assertThat(requete.getValue().objectifsDisponibles()).contains("Trouver un premier emploi");
    }
}

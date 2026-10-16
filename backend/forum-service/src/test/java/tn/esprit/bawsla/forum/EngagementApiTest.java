package tn.esprit.bawsla.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tn.esprit.bawsla.forum.entity.Notification;
import tn.esprit.bawsla.forum.entity.Reputation;
import tn.esprit.bawsla.forum.entity.enums.TypeNotification;
import tn.esprit.bawsla.forum.repository.BadgeObtenuRepository;
import tn.esprit.bawsla.forum.repository.NotificationRepository;
import tn.esprit.bawsla.forum.repository.ReputationRepository;

/** Lot 3 : suivis, favoris, lu / non lu, notifications, réputation, badges, classement, profil, statistiques. */
class EngagementApiTest extends BaseApiTest {

    private static final String TITRE = "Pourquoi mon LEFT JOIN renvoie autant de lignes ?";

    @Autowired
    private ReputationRepository reputations;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private BadgeObtenuRepository badges;

    // ---------------------------------------------------------------- Réputation

    @Test
    void lesVotesDonnentDesPointsEtLAnnulationLesRetireExactement() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, q, "Ta condition est dans le WHERE.");
        voter(SARA, "DISCUSSION", q, 1);
        voter(YASSINE, "REPONSE", r, 1);
        assertThat(points(YASSINE)).isEqualTo(5);
        assertThat(points(SARA)).isEqualTo(10);

        voter(SARA, "DISCUSSION", q, 0);
        assertThat(points(YASSINE)).isZero();

        donnerPoints(SARA, 60);
        voter(SARA, "DISCUSSION", q, -1);
        assertThat(points(YASSINE)).isEqualTo(-2);
        assertThat(points(SARA)).isEqualTo(10 + 60 - 1);
        voter(SARA, "DISCUSSION", q, 1);
        assertThat(points(YASSINE)).isEqualTo(5);
        assertThat(points(SARA)).isEqualTo(10 + 60);
    }

    @Test
    void lAcceptationRapporteQuinzePointsEtDeuxAQuiAccepteUneSeuleFois() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long rSara = repondre(SARA, q, "Ta condition est dans le WHERE.");
        long rImen = repondre(FORMATRICE, q, "Regarde la clause ON.");
        long rYassine = repondre(YASSINE, q, "J'ai trouvé tout seul.");

        accepter(YASSINE, q, rSara);
        assertThat(points(SARA)).isEqualTo(15);
        assertThat(points(YASSINE)).isEqualTo(2);

        accepter(YASSINE, q, rImen);
        assertThat(points(SARA)).isZero();
        assertThat(points(FORMATRICE)).isEqualTo(15);
        assertThat(points(YASSINE)).isEqualTo(2);

        accepter(YASSINE, q, rYassine);
        assertThat(points(FORMATRICE)).isZero();
        assertThat(points(YASSINE)).isEqualTo(2);

        en(YASSINE, ETUDIANT, delete("/api/forum/discussions/{id}/accepter", q)).andExpect(status().isOk());
        assertThat(points(YASSINE)).isZero();
    }

    @Test
    void approbationEtMasquageComptentEtSAnnulent() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, q, "Réponse détaillée.");
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/approbation", r)).andExpect(status().isOk());
        assertThat(points(SARA)).isEqualTo(20);
        en(FORMATRICE, ENSEIGNANT, delete("/api/forum/reponses/{r}/approbation", r)).andExpect(status().isOk());
        assertThat(points(SARA)).isZero();

        en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/masquer", r), MOTIF).andExpect(status().isOk());
        assertThat(points(SARA)).isEqualTo(-10);
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/retablir", r), MOTIF).andExpect(status().isOk());
        assertThat(points(SARA)).isZero();
    }

    @Test
    void laReputationDetailleNiveauProchainSeuilEtHistorique() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        for (int i = 0; i < 3; i++) {
            long r = repondre(SARA, q, "Réponse approuvée n° " + i);
            en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/approbation", r)).andExpect(status().isOk());
        }
        en(SARA, ETUDIANT, get("/api/forum/moi/reputation"))
                .andExpect(jsonPath("$.data.points").value(60))
                .andExpect(jsonPath("$.data.niveau").value("MATELOT"))
                .andExpect(jsonPath("$.data.niveauLibelle").value("Matelot"))
                .andExpect(jsonPath("$.data.prochainNiveau").value("NAVIGATEUR"))
                .andExpect(jsonPath("$.data.seuilProchainNiveau").value(200))
                .andExpect(jsonPath("$.data.historique", hasSize(3)))
                .andExpect(jsonPath("$.data.historique[0].raison").value("APPROBATION_FORMATEUR"));
        ouvrir(YASSINE, q).andExpect(jsonPath("$.data.reponses[0].auteur.niveau").value("MATELOT"));
    }

    // ---------------------------------------------------------------- Badges

    @Test
    void lesPremiersBadgesTombentAvecUneNotification() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, q, "Ta condition est dans le WHERE.");
        accepter(YASSINE, q, r);
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/approbation", r)).andExpect(status().isOk());

        assertThat(badges(YASSINE)).containsExactly("PREMIERE_QUESTION");
        assertThat(badges(SARA)).containsExactlyInAnyOrder("PREMIERE_REPONSE", "REPONSE_ACCEPTEE",
                "APPROUVE_FORMATEUR");
        assertThat(notifications.findAll()).filteredOn(n -> n.getType() == TypeNotification.BADGE)
                .extracting(Notification::getDestinataireId).contains(YASSINE, SARA);

        en(SARA, ETUDIANT, get("/api/forum/moi/reputation"))
                .andExpect(jsonPath("$.data.badges", hasSize(10)))
                .andExpect(jsonPath("$.data.badges[?(@.code=='PEDAGOGUE')].progression").value(1))
                .andExpect(jsonPath("$.data.badges[?(@.code=='PEDAGOGUE')].objectif").value(10))
                .andExpect(jsonPath("$.data.badges[?(@.code=='PEDAGOGUE')].obtenu").value(false));
    }

    @Test
    void repondreAUneQuestionSansReponseDepuisPlusDe24hDonneSecouriste() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long autre = publierQuestion(YASSINE, "Une autre question sur les index");
        repondre(SARA, autre, "Réponse rapide.");
        assertThat(badges(SARA)).doesNotContain("SECOURISTE");
        horloge.avancer(Duration.ofHours(25));
        repondre(SARA, q, "Je réponds enfin !");
        assertThat(badges(SARA)).contains("SECOURISTE");
    }

    @Test
    void septJoursDActiviteDAffileeDonnentAssidu() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        for (int jour = 1; jour < 7; jour++) {
            horloge.avancer(Duration.ofDays(1));
            en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/commentaires", q),
                    Map.of("contenu", "Jour " + jour)).andExpect(status().isCreated());
        }
        assertThat(badges(YASSINE)).contains("ASSIDU");
    }

    @Test
    void unJourSansActiviteRemetLaSerieAZero() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        horloge.avancer(Duration.ofDays(2));
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/commentaires", q), Map.of("contenu", "Retour"))
                .andExpect(status().isCreated());
        assertThat(reputations.findById(YASSINE).orElseThrow().getJoursActifsConsecutifs()).isEqualTo(1);
    }

    @Test
    void trouverSaReponseDansLesSimilairesDonneEclaireur() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/reponse-trouvee", q)).andExpect(status().isBadRequest());
        repondre(FORMATRICE, q, "Ta condition est dans le WHERE.");
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/reponse-trouvee", q))
                .andExpect(status().isBadRequest());
        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/reponse-trouvee", q)).andExpect(status().isOk());
        assertThat(badges(SARA)).containsExactly("ECLAIREUR");
        ouvrir(SARA, q).andExpect(jsonPath("$.data.suivie").value(true));
    }

    // ---------------------------------------------------------------- Suivis, favoris, lu / non lu

    @Test
    void lAuteurEtLesRepondantsSuiventLeFilAutomatiquement() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        ouvrir(YASSINE, q).andExpect(jsonPath("$.data.suivie").value(true))
                .andExpect(jsonPath("$.data.nbAbonnes").value(1));
        repondre(SARA, q, "Réponse.");
        ouvrir(SARA, q).andExpect(jsonPath("$.data.suivie").value(true))
                .andExpect(jsonPath("$.data.nbAbonnes").value(2));
        en(SARA, ETUDIANT, delete("/api/forum/discussions/{id}/suivre", q))
                .andExpect(jsonPath("$.data.suivie").value(false)).andExpect(jsonPath("$.data.nbAbonnes").value(1));
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/suivre", q))
                .andExpect(jsonPath("$.data.nbAbonnes").value(2));
        en(FORMATRICE, ENSEIGNANT, get("/api/forum/discussions").param("filtre", "suivies"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void lesFavorisSAjoutentSeRetirentEtSeFiltrent() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        publierQuestion(YASSINE, "Une autre question sur les index");
        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/favori", q)).andExpect(jsonPath("$.data.favori").value(true));
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("filtre", "favoris"))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.contenu[0].id").value(q));
        en(SARA, ETUDIANT, delete("/api/forum/discussions/{id}/favori", q))
                .andExpect(jsonPath("$.data.favori").value(false));
    }

    @Test
    void toutMarquerCommeLuVideLesNonLus() throws Exception {
        publierQuestion(YASSINE, TITRE);
        publierQuestion(YASSINE, "Une autre question sur les index");
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("filtre", "non-lues"))
                .andExpect(jsonPath("$.data.total").value(2));
        en(SARA, ETUDIANT, post("/api/forum/discussions/lu").param("coursId", "5"))
                .andExpect(jsonPath("$.data").value(2));
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("filtre", "non-lues"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ---------------------------------------------------------------- Notifications

    @Test
    void lesAbonnesSontNotifiesDUneReponseSaufSonAuteur() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        repondre(SARA, q, "Ta condition est dans le WHERE au lieu du ON.");

        // 3 = le badge « Première question », la réponse de La Vigie prête et celle de Sara (la plus récente en tête).
        en(YASSINE, ETUDIANT, get("/api/forum/notifications/compteur")).andExpect(jsonPath("$.data.nonLues").value(3));
        en(YASSINE, ETUDIANT, get("/api/forum/notifications"))
                .andExpect(jsonPath("$.data.contenu[0].type").value("REPONSE"))
                .andExpect(jsonPath("$.data.contenu[0].discussionTitre").value(TITRE))
                .andExpect(jsonPath("$.data.contenu[0].acteur.nom").value("Sara Mansouri"))
                .andExpect(jsonPath("$.data.contenu[0].extrait").value("Ta condition est dans le WHERE au lieu du ON."));
        // Sara n'est pas notifiée de sa propre réponse : seule reste la notification de son badge.
        en(SARA, ETUDIANT, get("/api/forum/notifications").param("nonLues", "true"))
                .andExpect(jsonPath("$.data.contenu", hasSize(1)))
                .andExpect(jsonPath("$.data.contenu[0].type").value("BADGE"));
    }

    @Test
    void uneReponseAnonymeNeDevoilePasSonAuteurDansLaNotification() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/reponses", q),
                Map.of("contenu", "Réponse anonyme", "anonyme", true)).andExpect(status().isCreated());
        en(YASSINE, ETUDIANT, get("/api/forum/notifications"))
                .andExpect(jsonPath("$.data.contenu[0].type").value("REPONSE"))
                .andExpect(jsonPath("$.data.contenu[0].acteur").doesNotExist());
    }

    @Test
    void uneMentionRemplaceLaNotificationDeReponseEtUnCommentairePrevientLAuteur() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long rImen = repondre(FORMATRICE, q, "Regarde la clause ON.");
        repondre(SARA, q, "Je suis d'accord avec @Imen Trabelsi, et toi @Yassine Gharbi ?");
        // La question de ce test sort du texte de démonstration : La Vigie s'abstient et prévient la formatrice.
        assertThat(types(FORMATRICE)).containsExactlyInAnyOrder("HORS_COURS", "MENTION");
        assertThat(types(YASSINE)).containsExactlyInAnyOrder("VIGIE_PRETE", "REPONSE", "MENTION");

        en(YASSINE, ETUDIANT, post("/api/forum/reponses/{r}/commentaires", rImen), Map.of("contenu", "Merci !"))
                .andExpect(status().isCreated());
        assertThat(types(FORMATRICE)).containsExactlyInAnyOrder("HORS_COURS", "MENTION", "COMMENTAIRE");
    }

    @Test
    void acceptationApprobationEtMasquagePreviennentLAuteurDeLaReponse() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, q, "Réponse.");
        long r2 = repondre(SARA, q, "Réponse à masquer.");
        accepter(YASSINE, q, r);
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/approbation", r)).andExpect(status().isOk());
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/masquer", r2), MOTIF).andExpect(status().isOk());
        assertThat(types(SARA)).contains("REPONSE_ACCEPTEE", "APPROBATION", "MESSAGE_MASQUE");
    }

    @Test
    void lesNotificationsSeMarquentLuesUneParUneOuToutes() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        repondre(SARA, q, "Première.");
        repondre(FORMATRICE, q, "Deuxième.");
        long premiere = notifications.findAll().stream().filter(n -> n.getDestinataireId().equals(YASSINE))
                .findFirst().orElseThrow().getId();
        // Yassine a 4 notifications : son badge « Première question », la réponse de La Vigie, puis les deux réponses.
        en(YASSINE, ETUDIANT, post("/api/forum/notifications/lues"), List.of(premiere))
                .andExpect(jsonPath("$.data.nonLues").value(3));
        en(YASSINE, ETUDIANT, get("/api/forum/notifications").param("nonLues", "true"))
                .andExpect(jsonPath("$.data.contenu", hasSize(3)));
        en(YASSINE, ETUDIANT, post("/api/forum/notifications/lues")).andExpect(jsonPath("$.data.nonLues").value(0));
        // Marquer la notification d'un autre membre n'a aucun effet (Sara garde son badge et la réponse d'Imen).
        en(SARA, ETUDIANT, post("/api/forum/notifications/lues"), List.of(premiere))
                .andExpect(jsonPath("$.data.nonLues").value(2));
        assertThat(notifications.findById(premiere).orElseThrow().isLue()).isTrue();
    }

    // ---------------------------------------------------------------- Profil, activité, classement

    @Test
    void leProfilPublicNeCompteNiNeReliePasLesMessagesAnonymes() throws Exception {
        publierQuestion(YASSINE, TITRE);
        Map<String, Object> anonyme = question(COURS, "Question posée anonymement");
        anonyme.put("anonyme", true);
        anonyme.put("etiquettes", List.of("secret"));
        publier(YASSINE, anonyme);

        en(SARA, ETUDIANT, get("/api/forum/profils/{id}", YASSINE))
                .andExpect(jsonPath("$.data.nom").value("Yassine Gharbi"))
                .andExpect(jsonPath("$.data.nbQuestions").value(1))
                .andExpect(jsonPath("$.data.etiquettesFavorites", not(hasItem("secret"))))
                .andExpect(jsonPath("$.data.badges[0].code").value("PREMIERE_QUESTION"));
        en(YASSINE, ETUDIANT, get("/api/forum/profils/{id}", YASSINE))
                .andExpect(jsonPath("$.data.nbQuestions").value(2));
    }

    @Test
    void monActiviteRegroupeQuestionsReponsesFavorisEtSuivis() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long autre = publierQuestion(SARA, "Comment créer un index composite ?");
        repondre(YASSINE, autre, "Avec CREATE INDEX sur plusieurs colonnes.");
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/favori", autre)).andExpect(status().isOk());

        en(YASSINE, ETUDIANT, get("/api/forum/moi/activite"))
                .andExpect(jsonPath("$.data.questions[0].id").value(q))
                .andExpect(jsonPath("$.data.reponses[0].id").value(autre))
                .andExpect(jsonPath("$.data.favoris[0].id").value(autre))
                .andExpect(jsonPath("$.data.suivis", hasSize(2)))
                .andExpect(jsonPath("$.data.reputation.badges", hasSize(10)));
    }

    @Test
    void leClassementDeLaSemaineRangeLesContributeursDuCours() throws Exception {
        long q = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, q, "Réponse.");
        accepter(YASSINE, q, r);
        en(YASSINE, ETUDIANT, get("/api/forum/classement").param("coursId", "5"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].rang").value(1))
                .andExpect(jsonPath("$.data[0].auteur.nom").value("Sara Mansouri"))
                .andExpect(jsonPath("$.data[0].points").value(15))
                .andExpect(jsonPath("$.data[1].points").value(2));
        en(YASSINE, ETUDIANT, get("/api/forum/classement").param("coursId", "5").param("periode", "annee"))
                .andExpect(status().isBadRequest());
        horloge.avancer(Duration.ofDays(8));
        en(YASSINE, ETUDIANT, get("/api/forum/classement").param("coursId", "5"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ---------------------------------------------------------------- Statistiques

    @Test
    void lesStatistiquesDuCoursSontReserveesAuFormateurDuCoursEtALAdmin() throws Exception {
        long q1 = publierQuestion(YASSINE, TITRE);
        Map<String, Object> q2 = question(COURS, "Question du chapitre 3 sur les vues");
        q2.put("chapitreId", 3);
        publier(SARA, q2);
        long r = repondre(SARA, q1, "Réponse.");
        accepter(YASSINE, q1, r);

        en(YASSINE, ETUDIANT, get("/api/forum/statistiques").param("coursId", "5")).andExpect(status().isForbidden());
        en(AUTRE_FORMATEUR, ENSEIGNANT, get("/api/forum/statistiques").param("coursId", "5"))
                .andExpect(status().isForbidden());
        en(FORMATRICE, ENSEIGNANT, get("/api/forum/statistiques")).andExpect(status().isForbidden());
        en(FORMATRICE, ENSEIGNANT, get("/api/forum/statistiques").param("coursId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions").value(2))
                .andExpect(jsonPath("$.data.sansReponse").value(1))
                .andExpect(jsonPath("$.data.tauxResolution").value(0.5))
                .andExpect(jsonPath("$.data.delaiPremiereReponseHeures").value(0.0))
                .andExpect(jsonPath("$.data.parChapitre", hasSize(2)))
                .andExpect(jsonPath("$.data.parSemaine", hasSize(1)));
        en(ADMIN, ADMIN_ROLE, get("/api/forum/statistiques").param("periode", "7"))
                .andExpect(jsonPath("$.data.questions").value(2));
        en(ADMIN, ADMIN_ROLE, get("/api/forum/statistiques").param("periode", "12"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Aides

    private void voter(long votant, String cible, long cibleId, int valeur) throws Exception {
        en(votant, role(votant), put("/api/forum/votes"), Map.of("cibleType", cible, "cibleId", cibleId,
                "valeur", valeur)).andExpect(status().isOk());
    }

    private void accepter(long user, long q, long r) throws Exception {
        en(user, role(user), post("/api/forum/discussions/{id}/accepter/{r}", q, r)).andExpect(status().isOk());
    }

    private int points(long userId) {
        return reputations.findById(userId).map(Reputation::getPoints).orElse(0);
    }

    private void donnerPoints(long userId, int points) {
        Reputation r = reputations.findById(userId).orElseGet(() -> {
            Reputation n = new Reputation();
            n.setUserId(userId);
            return n;
        });
        r.setPoints(r.getPoints() + points);
        reputations.save(r);
    }

    private List<String> badges(long userId) {
        return badges.findByUserId(userId).stream().map(b -> b.getCode().name()).toList();
    }

    private List<String> types(long userId) {
        return notifications.findAll().stream().filter(n -> n.getDestinataireId().equals(userId))
                .filter(n -> n.getType() != TypeNotification.BADGE).map(n -> n.getType().name()).toList();
    }
}

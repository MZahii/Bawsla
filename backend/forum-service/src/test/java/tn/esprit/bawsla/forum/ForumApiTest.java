package tn.esprit.bawsla.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import tn.esprit.bawsla.forum.entity.ParametresForumCours;
import tn.esprit.bawsla.forum.entity.Reputation;
import tn.esprit.bawsla.forum.entity.Sanction;
import tn.esprit.bawsla.forum.entity.enums.TypeSanction;
import tn.esprit.bawsla.forum.repository.JournalModerationRepository;
import tn.esprit.bawsla.forum.repository.ParametresForumCoursRepository;
import tn.esprit.bawsla.forum.repository.ReputationRepository;
import tn.esprit.bawsla.forum.repository.SanctionRepository;

/**
 * API du forum, cœur web (lot 2) : droits croisés (auteur, formateur du cours, autre formateur, admin), anonymat,
 * vote unique, meilleure réponse, approbation, verrouillage, masquage, anti-flood, sanctions, dégradation propre.
 */
class ForumApiTest extends BaseApiTest {

    private static final String TITRE = "Pourquoi mon LEFT JOIN renvoie autant de lignes ?";

    @Autowired
    private ReputationRepository reputations;

    @Autowired
    private SanctionRepository sanctions;

    @Autowired
    private ParametresForumCoursRepository parametresCours;

    @Autowired
    private JournalModerationRepository journal;

    // ---------------------------------------------------------------- Authentification et accès

    @Test
    void sansPasserParLaGatewayOnObtient401() throws Exception {
        mvc.perform(get("/api/forum/discussions")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unRoleInconnuEstRefuse() throws Exception {
        en(99L, "PIRATE", get("/api/forum/discussions")).andExpect(status().isForbidden());
    }

    @Test
    void unCoursInvisiblePourCourseServiceDonne404() throws Exception {
        en(YASSINE, ETUDIANT, post("/api/forum/discussions"), question(COURS_ABSENT, TITRE))
                .andExpect(status().isNotFound());
        en(YASSINE, ETUDIANT, get("/api/forum/discussions").param("coursId", "404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void siCoursServiceNeRepondPasLeForumSeDegradeEtSeulLAdminModere() throws Exception {
        long id = publier(YASSINE, question(COURS_INJOIGNABLE, TITRE));
        ouvrir(SARA, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coursTitre").value("Cours n° 77"))
                .andExpect(jsonPath("$.data.droits.moderer").value(false));
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/epingler", id)).andExpect(status().isForbidden());
        en(ADMIN, ADMIN_ROLE, post("/api/forum/discussions/{id}/epingler", id)).andExpect(status().isOk());
    }

    @Test
    void siUserServiceNeRepondPasLAuteurDevientMembreNumero() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        org.mockito.Mockito.when(userClient.getUser(YASSINE)).thenThrow(new IllegalStateException("arrêté"));
        annuaire.oublierCaches();
        ouvrir(SARA, id).andExpect(jsonPath("$.data.auteur.nom").value("Membre n° 30"));
    }

    // ---------------------------------------------------------------- Publication et lecture

    @Test
    void unApprenantPublieUneQuestionEtLaRetrouveDansLaListe() throws Exception {
        en(YASSINE, ETUDIANT, post("/api/forum/discussions"), question(COURS, TITRE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.statut").value("OUVERTE"))
                .andExpect(jsonPath("$.data.coursTitre").value("Bases de données"))
                .andExpect(jsonPath("$.data.etiquettes[0]").value("jointures"))
                .andExpect(jsonPath("$.data.etiquettes[1]").value("sql"))
                .andExpect(jsonPath("$.data.auteur.nom").value("Yassine Gharbi"))
                .andExpect(jsonPath("$.data.droits.modifier").value(true))
                .andExpect(jsonPath("$.data.droits.moderer").value(false));

        en(SARA, ETUDIANT, get("/api/forum/discussions").param("coursId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.contenu[0].titre").value(TITRE))
                .andExpect(jsonPath("$.data.contenu[0].extrait", containsString("LEFT JOIN renvoie")))
                .andExpect(jsonPath("$.data.contenu[0].nonLue").value(true));
    }

    @Test
    void lesDonneesInvalidesSontRefuseesAvecLeDetailParChamp() throws Exception {
        Map<String, Object> corps = question(COURS, "Court");
        corps.put("etiquettes", List.of("a", "b", "c", "d", "e", "f"));
        en(YASSINE, ETUDIANT, post("/api/forum/discussions"), corps)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.titre").exists())
                .andExpect(jsonPath("$.details.etiquettes").exists());
    }

    @Test
    void uneVueCompteUneFoisParPersonneEtParJour() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        ouvrir(SARA, id);
        ouvrir(SARA, id);
        ouvrir(FORMATRICE, id).andExpect(jsonPath("$.data.nbVues").value(2));
    }

    @Test
    void ouvrirUnFilLeMarqueCommeLu() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("filtre", "non-lues"))
                .andExpect(jsonPath("$.data.total").value(1));
        ouvrir(SARA, id);
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("filtre", "non-lues"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void lesFiltresEtLesTrisSAppliquent() throws Exception {
        long a = publierQuestion(YASSINE, TITRE);
        Map<String, Object> autre = question(COURS, "Comment fonctionne une clé étrangère ?");
        autre.put("etiquettes", List.of("modélisation"));
        autre.put("contenu", "Je ne comprends pas les clés étrangères.");
        long b = publier(SARA, autre);
        repondre(SARA, a, "Vérifie ta clause WHERE.");
        en(FORMATRICE, ENSEIGNANT, put("/api/forum/votes"), Map.of("cibleType", "DISCUSSION", "cibleId", b,
                "valeur", 1)).andExpect(status().isOk());

        liste(YASSINE, "statut", "SANS_REPONSE").andExpect(jsonPath("$.data.contenu[0].id").value(b))
                .andExpect(jsonPath("$.data.total").value(1));
        liste(YASSINE, "filtre", "miennes").andExpect(jsonPath("$.data.contenu[0].id").value(a))
                .andExpect(jsonPath("$.data.total").value(1));
        liste(SARA, "filtre", "repondues").andExpect(jsonPath("$.data.contenu[0].id").value(a));
        liste(SARA, "etiquette", "Modélisation").andExpect(jsonPath("$.data.contenu[0].id").value(b));
        liste(SARA, "q", "clés étrangères").andExpect(jsonPath("$.data.total").value(1));
        liste(SARA, "tri", "votes").andExpect(jsonPath("$.data.contenu[0].id").value(b));
        liste(SARA, "filtre", "inconnu").andExpect(status().isBadRequest());
    }

    @Test
    void laPaginationIndiqueSIlResteDesFils() throws Exception {
        for (int i = 0; i < 3; i++) {
            publier(FORMATRICE, question(COURS, "Question de démonstration n° " + i));
        }
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("taille", "2"))
                .andExpect(jsonPath("$.data.contenu", hasSize(2)))
                .andExpect(jsonPath("$.data.suivante").value(true));
        en(SARA, ETUDIANT, get("/api/forum/discussions").param("taille", "2").param("page", "1"))
                .andExpect(jsonPath("$.data.contenu", hasSize(1)))
                .andExpect(jsonPath("$.data.suivante").value(false));
    }

    // ---------------------------------------------------------------- Anonymat

    @Test
    void unMessageAnonymeCacheLAuteurAuxApprenantsMaisPasAuFormateurDuCours() throws Exception {
        Map<String, Object> corps = question(COURS, TITRE);
        corps.put("anonyme", true);
        long id = publier(YASSINE, corps);

        ouvrir(SARA, id).andExpect(jsonPath("$.data.auteur.anonyme").value(true))
                .andExpect(jsonPath("$.data.auteur.id").doesNotExist())
                .andExpect(jsonPath("$.data.auteur.nom").doesNotExist());
        en(SARA, ETUDIANT, get("/api/forum/discussions"))
                .andExpect(jsonPath("$.data.contenu[0].auteur.id").doesNotExist())
                .andExpect(jsonPath("$.data.contenu[0].dernierActeur").doesNotExist());
        ouvrir(FORMATRICE, id).andExpect(jsonPath("$.data.auteur.id").value(YASSINE))
                .andExpect(jsonPath("$.data.auteur.anonyme").value(true));
        ouvrir(AUTRE_FORMATEUR, id).andExpect(jsonPath("$.data.auteur.id").doesNotExist());
        ouvrir(YASSINE, id).andExpect(jsonPath("$.data.auteur.id").value(YASSINE));
    }

    @Test
    void lAnonymatEstReserveAuxApprenantsEtPeutEtreDesactive() throws Exception {
        Map<String, Object> corps = question(COURS, TITRE);
        corps.put("anonyme", true);
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions"), corps).andExpect(status().isBadRequest());

        ParametresForumCours p = new ParametresForumCours();
        p.setCoursId(COURS);
        p.setAnonymatAutorise(false);
        parametresCours.save(p);
        en(YASSINE, ETUDIANT, post("/api/forum/discussions"), corps).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("anonymat")));
    }

    // ---------------------------------------------------------------- Annonces, édition, suppression

    @Test
    void seulLeFormateurDuCoursPublieUneAnnonceEpingleeEnTete() throws Exception {
        long question = publierQuestion(YASSINE, TITRE);
        Map<String, Object> annonce = question(COURS, "Examen final : ce qu'il faut réviser");
        annonce.put("type", "ANNONCE");
        en(YASSINE, ETUDIANT, post("/api/forum/discussions"), annonce).andExpect(status().isForbidden());
        en(AUTRE_FORMATEUR, ENSEIGNANT, post("/api/forum/discussions"), annonce).andExpect(status().isForbidden());
        long id = publier(FORMATRICE, annonce);

        en(SARA, ETUDIANT, get("/api/forum/discussions").param("tri", "recentes"))
                .andExpect(jsonPath("$.data.contenu[0].id").value(id))
                .andExpect(jsonPath("$.data.contenu[0].epinglee").value(true))
                .andExpect(jsonPath("$.data.contenu[1].id").value(question));
    }

    @Test
    void lAuteurModifieSaQuestionEtLHistoriqueGardeLaVersionPrecedente() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        Map<String, Object> modif = Map.of("titre", "LEFT JOIN : autant de lignes qu'un INNER JOIN ?",
                "contenu", "Contenu précisé avec ma requête.", "etiquettes", List.of("sql"));
        en(SARA, ETUDIANT, put("/api/forum/discussions/{id}", id), modif).andExpect(status().isForbidden());
        en(YASSINE, ETUDIANT, put("/api/forum/discussions/{id}", id), modif).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modifieLe").exists())
                .andExpect(jsonPath("$.data.etiquettes", hasSize(1)));

        en(SARA, ETUDIANT, get("/api/forum/discussions/{id}/versions", id))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].titre").value(TITRE));
    }

    @Test
    void uneDiscussionSupprimeeDisparaitPourTous() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        en(SARA, ETUDIANT, delete("/api/forum/discussions/{id}", id)).andExpect(status().isForbidden());
        en(FORMATRICE, ENSEIGNANT, delete("/api/forum/discussions/{id}", id)).andExpect(status().isOk());
        ouvrir(YASSINE, id).andExpect(status().isNotFound());
        en(YASSINE, ETUDIANT, get("/api/forum/discussions")).andExpect(jsonPath("$.data.total").value(0));
        assertThat(journal.findAll()).extracting(j -> j.getAction().name()).containsExactly("SUPPRIMER");
    }

    // ---------------------------------------------------------------- Réponses, meilleure réponse, approbation

    @Test
    void lAuteurDeLaQuestionChoisitLaMeilleureReponseEtLeFilPasseEnResolue() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        long r1 = repondre(SARA, id, "Ta condition est dans le WHERE au lieu du ON.");
        long r2 = repondre(FORMATRICE, id, "Regarde aussi les doublons de la table de droite.");

        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/accepter/{r}", id, r1)).andExpect(status().isForbidden());
        en(AUTRE_FORMATEUR, ENSEIGNANT, post("/api/forum/discussions/{id}/accepter/{r}", id, r1))
                .andExpect(status().isForbidden());
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/accepter/{r}", id, r1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("RESOLUE"))
                .andExpect(jsonPath("$.data.reponseAccepteeId").value(r1))
                .andExpect(jsonPath("$.data.reponses[0].id").value(r1))
                .andExpect(jsonPath("$.data.reponses[0].acceptee").value(true));

        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/accepter/{r}", id, r2))
                .andExpect(jsonPath("$.data.reponseAccepteeId").value(r2))
                .andExpect(jsonPath("$.data.reponses[0].id").value(r2))
                .andExpect(jsonPath("$.data.reponses[1].acceptee").value(false));

        en(YASSINE, ETUDIANT, delete("/api/forum/discussions/{id}/accepter", id))
                .andExpect(jsonPath("$.data.statut").value("OUVERTE"))
                .andExpect(jsonPath("$.data.reponseAccepteeId").doesNotExist());
    }

    @Test
    void supprimerLaReponseAccepteeRouvreLaQuestion() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, id, "Ta condition est dans le WHERE au lieu du ON.");
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/accepter/{r}", id, r)).andExpect(status().isOk());
        en(SARA, ETUDIANT, delete("/api/forum/reponses/{r}", r)).andExpect(status().isOk());
        ouvrir(YASSINE, id).andExpect(jsonPath("$.data.statut").value("OUVERTE"))
                .andExpect(jsonPath("$.data.nbReponses").value(0))
                .andExpect(jsonPath("$.data.reponses", hasSize(0)));
    }

    @Test
    void uneDiscussionLibreNAPasDeMeilleureReponse() throws Exception {
        Map<String, Object> corps = question(COURS, "Vos ressources préférées pour SQL ?");
        corps.put("type", "DISCUSSION");
        long id = publier(YASSINE, corps);
        long r = repondre(SARA, id, "Le site SQLZoo.");
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/accepter/{r}", id, r))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seulLeFormateurDuCoursApprouveUneReponseEtElleRemonte() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        long r1 = repondre(SARA, id, "Première réponse.");
        long r2 = repondre(YASSINE, id, "Deuxième réponse, avec un exemple.");

        en(AUTRE_FORMATEUR, ENSEIGNANT, post("/api/forum/reponses/{r}/approbation", r2))
                .andExpect(status().isForbidden());
        en(SARA, ETUDIANT, post("/api/forum/reponses/{r}/approbation", r2)).andExpect(status().isForbidden());
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/approbation", r2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approuvee").value(true))
                .andExpect(jsonPath("$.data.approuveePar.nom").value("Imen Trabelsi"))
                .andExpect(jsonPath("$.data.approuveePar.formateurDuCours").value(true));

        ouvrir(SARA, id).andExpect(jsonPath("$.data.reponses[0].id").value(r2))
                .andExpect(jsonPath("$.data.reponses[1].id").value(r1));
        en(SARA, ETUDIANT, get("/api/forum/discussions"))
                .andExpect(jsonPath("$.data.contenu[0].approuvee").value(true));
    }

    // ---------------------------------------------------------------- Commentaires

    @Test
    void lesCommentairesSontCourtsEtRangesSousLeurMessage() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, id, "Vérifie ta clause ON.");
        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/commentaires", id), Map.of("contenu", "x".repeat(601)))
                .andExpect(status().isBadRequest());
        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/commentaires", id),
                Map.of("contenu", "Tu peux montrer ta requête ?")).andExpect(status().isCreated());
        en(YASSINE, ETUDIANT, post("/api/forum/reponses/{r}/commentaires", r), Map.of("contenu", "Merci !"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reponseId").value(r));

        ouvrir(FORMATRICE, id).andExpect(jsonPath("$.data.commentaires", hasSize(1)))
                .andExpect(jsonPath("$.data.reponses[0].commentaires[0].contenu").value("Merci !"));
    }

    // ---------------------------------------------------------------- Votes et réactions

    @Test
    void unVoteParPersonneAnnulableEtJamaisSurSonPropreMessage() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        Map<String, Object> plus = Map.of("cibleType", "DISCUSSION", "cibleId", id, "valeur", 1);
        en(SARA, ETUDIANT, put("/api/forum/votes"), plus).andExpect(jsonPath("$.data.score").value(1));
        en(SARA, ETUDIANT, put("/api/forum/votes"), plus).andExpect(jsonPath("$.data.score").value(1))
                .andExpect(jsonPath("$.data.monVote").value(1));
        en(FORMATRICE, ENSEIGNANT, put("/api/forum/votes"), plus).andExpect(jsonPath("$.data.score").value(2));
        en(SARA, ETUDIANT, put("/api/forum/votes"), Map.of("cibleType", "DISCUSSION", "cibleId", id, "valeur", 0))
                .andExpect(jsonPath("$.data.score").value(1)).andExpect(jsonPath("$.data.monVote").value(0));
        en(YASSINE, ETUDIANT, put("/api/forum/votes"), plus).andExpect(status().isForbidden());
        en(SARA, ETUDIANT, put("/api/forum/votes"), Map.of("cibleType", "COMMENTAIRE", "cibleId", 1, "valeur", 1))
                .andExpect(status().isBadRequest());
        en(SARA, ETUDIANT, put("/api/forum/votes"), Map.of("cibleType", "DISCUSSION", "cibleId", id, "valeur", 2))
                .andExpect(status().isBadRequest());
        ouvrir(FORMATRICE, id).andExpect(jsonPath("$.data.monVote").value(1));
    }

    @Test
    void leVoteNegatifDemandeCinquantePointsDeReputation() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, id, "Réponse discutable.");
        Map<String, Object> moins = Map.of("cibleType", "REPONSE", "cibleId", r, "valeur", -1);
        en(YASSINE, ETUDIANT, put("/api/forum/votes"), moins).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("50 points")));

        Reputation rep = new Reputation();
        rep.setUserId(YASSINE);
        rep.setPoints(60);
        reputations.save(rep);
        en(YASSINE, ETUDIANT, put("/api/forum/votes"), moins).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(-1));
    }

    @Test
    void lesReactionsSontIdempotentesEtPasSurSaPropreReponse() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, id, "Ta condition est dans le WHERE.");
        en(YASSINE, ETUDIANT, put("/api/forum/reponses/{r}/reactions/MERCI", r)).andExpect(status().isOk());
        en(YASSINE, ETUDIANT, put("/api/forum/reponses/{r}/reactions/MERCI", r))
                .andExpect(jsonPath("$.data.compteurs.MERCI").value(1))
                .andExpect(jsonPath("$.data.miennes[0]").value("MERCI"));
        en(FORMATRICE, ENSEIGNANT, put("/api/forum/reponses/{r}/reactions/PILE", r))
                .andExpect(jsonPath("$.data.compteurs.PILE").value(1));
        en(SARA, ETUDIANT, put("/api/forum/reponses/{r}/reactions/MERCI", r)).andExpect(status().isForbidden());
        en(YASSINE, ETUDIANT, put("/api/forum/reponses/{r}/reactions/BRAVO", r)).andExpect(status().isBadRequest());
        en(YASSINE, ETUDIANT, delete("/api/forum/reponses/{r}/reactions/MERCI", r))
                .andExpect(jsonPath("$.data.compteurs.MERCI").doesNotExist());
    }

    // ---------------------------------------------------------------- Modération

    @Test
    void unFilVerrouilleNAccepteQueLesMessagesDuFormateur() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        en(AUTRE_FORMATEUR, ENSEIGNANT, post("/api/forum/discussions/{id}/verrouiller", id))
                .andExpect(status().isForbidden());
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/verrouiller", id),
                Map.of("motif", "Hors sujet")).andExpect(jsonPath("$.data.statut").value("FERMEE"));

        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/reponses", id),
                Map.of("contenu", "Encore une idée", "anonyme", false)).andExpect(status().isForbidden());
        repondre(FORMATRICE, id, "Je ferme ce fil : voir le chapitre 3.");
        en(FORMATRICE, ENSEIGNANT, delete("/api/forum/discussions/{id}/verrouiller", id))
                .andExpect(jsonPath("$.data.statut").value("OUVERTE"));
        assertThat(journal.findAll()).extracting(j -> j.getAction().name())
                .containsExactly("VERROUILLER", "DEVERROUILLER");
        assertThat(journal.findAll().get(0).getMotif()).isEqualTo("Hors sujet");
    }

    @Test
    void unMessageMasqueNEstVisibleQueParSonAuteurEtLesModerateurs() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        long r = repondre(SARA, id, "Contenu à masquer.");
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/reponses/{r}/masquer", r), MOTIF).andExpect(status().isOk());
        ouvrir(YASSINE, id).andExpect(jsonPath("$.data.reponses[0].masquee").value(true))
                .andExpect(jsonPath("$.data.reponses[0].contenu").doesNotExist());
        ouvrir(SARA, id).andExpect(jsonPath("$.data.reponses[0].contenu").value("Contenu à masquer."));

        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/masquer", id), MOTIF).andExpect(status().isOk());
        ouvrir(SARA, id).andExpect(status().isNotFound());
        ouvrir(YASSINE, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.masquee").value(true));
        en(SARA, ETUDIANT, get("/api/forum/discussions")).andExpect(jsonPath("$.data.total").value(0));
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/retablir", id), MOTIF).andExpect(status().isOk());
        ouvrir(SARA, id).andExpect(status().isOk());
    }

    @Test
    void unDoublonFusionneRenvoieVersLOriginaleEtSeFerme() throws Exception {
        long originale = publierQuestion(YASSINE, TITRE);
        long doublon = publierQuestion(SARA, "LEFT JOIN qui se comporte comme INNER JOIN");
        long autreCours = publier(SARA, question(COURS_INJOIGNABLE, "Question dans un autre cours"));

        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/fusionner", doublon),
                Map.of("versDiscussionId", autreCours, "motif", "Doublon de la question d'origine")).andExpect(status().isBadRequest());
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/fusionner", doublon),
                Map.of("versDiscussionId", originale, "motif", "Doublon de la question d'origine")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fusionneeDansId").value(originale))
                .andExpect(jsonPath("$.data.verrouillee").value(true));
        en(YASSINE, ETUDIANT, post("/api/forum/discussions/{id}/reponses", doublon),
                Map.of("contenu", "Réponse", "anonyme", false)).andExpect(status().isForbidden());
    }

    @Test
    void leFormateurDeplaceUnFilEtModifieSesEtiquettes() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        en(FORMATRICE, ENSEIGNANT, post("/api/forum/discussions/{id}/deplacer", id),
                Map.of("chapitreId", 3, "page", 12, "motif", "Question du chapitre 3, page 12")).andExpect(jsonPath("$.data.chapitreId").value(3))
                .andExpect(jsonPath("$.data.page").value(12));
        en(FORMATRICE, ENSEIGNANT, put("/api/forum/discussions/{id}/etiquettes", id),
                Map.of("etiquettes", List.of("LEFT JOIN"))).andExpect(jsonPath("$.data.etiquettes[0]").value("left join"));
        en(YASSINE, ETUDIANT, put("/api/forum/discussions/{id}/etiquettes", id),
                Map.of("etiquettes", List.of("x"))).andExpect(status().isForbidden());
    }

    @Test
    void lesEtiquettesSAutocompletentParCoursPuisSurLaPlateforme() throws Exception {
        publierQuestion(YASSINE, TITRE);
        en(SARA, ETUDIANT, get("/api/forum/etiquettes").param("coursId", "5").param("q", "jo"))
                .andExpect(jsonPath("$.data[0].libelle").value("jointures"))
                .andExpect(jsonPath("$.data[0].nbUtilisations").value(1));
        en(SARA, ETUDIANT, get("/api/forum/etiquettes").param("q", "s"))
                .andExpect(jsonPath("$.data[0].libelle").value("sql"));
    }

    // ---------------------------------------------------------------- Limites, sanctions, forum fermé

    @Test
    void auDelaDeCinqQuestionsParHeureOnObtient429SaufLeFormateur() throws Exception {
        for (int i = 1; i <= 5; i++) {
            publierQuestion(YASSINE, "Ma question numéro " + i + " sur SQL");
        }
        en(YASSINE, ETUDIANT, post("/api/forum/discussions"), question(COURS, "Ma sixième question sur SQL"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"));
        for (int i = 1; i <= 6; i++) {
            publier(FORMATRICE, question(COURS, "Question de la formatrice n° " + i));
        }
    }

    @Test
    void unMembreEnLectureSeuleLitMaisNEcritPlus() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        Sanction s = new Sanction();
        s.setUserId(SARA);
        s.setType(TypeSanction.LECTURE_SEULE);
        s.setMotif("Messages répétés hors sujet");
        s.setDecidePar(ADMIN);
        s.setDateFin(LocalDateTime.now().plusDays(7));
        sanctions.save(s);

        ouvrir(SARA, id).andExpect(status().isOk());
        en(SARA, ETUDIANT, post("/api/forum/discussions/{id}/reponses", id),
                Map.of("contenu", "Je réponds quand même", "anonyme", false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("suspendu jusqu'au")));
        en(SARA, ETUDIANT, put("/api/forum/votes"), Map.of("cibleType", "DISCUSSION", "cibleId", id, "valeur", 1))
                .andExpect(status().isForbidden());
    }

    @Test
    void unAvertissementOuUneSanctionLeveeNEmpechePasDEcrire() throws Exception {
        long id = publierQuestion(YASSINE, TITRE);
        Sanction avertissement = new Sanction();
        avertissement.setUserId(SARA);
        avertissement.setType(TypeSanction.AVERTISSEMENT);
        avertissement.setMotif("Attention au ton");
        avertissement.setDecidePar(ADMIN);
        sanctions.save(avertissement);
        Sanction levee = new Sanction();
        levee.setUserId(SARA);
        levee.setType(TypeSanction.SUSPENSION);
        levee.setMotif("Erreur");
        levee.setDecidePar(ADMIN);
        levee.setLeveeLe(LocalDateTime.now());
        sanctions.save(levee);
        repondre(SARA, id, "Je peux encore répondre.");
    }

    @Test
    void unForumFermeNAccepteQueLeFormateur() throws Exception {
        ParametresForumCours p = new ParametresForumCours();
        p.setCoursId(COURS);
        p.setOuvert(false);
        parametresCours.save(p);
        en(YASSINE, ETUDIANT, post("/api/forum/discussions"), question(COURS, TITRE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message", containsString("fermé")));
        publier(FORMATRICE, question(COURS, "La formatrice publie quand même"));
    }

    // ---------------------------------------------------------------- Pièces jointes

    @Test
    void uneImageEstAccepteeSurSesOctetsMagiquesEtRelue() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        String reponse = mvc.perform(multipart("/api/forum/pieces-jointes")
                        .file(new MockMultipartFile("fichier", "capture.png", "image/png", png))
                        .header("X-User-Id", YASSINE).header("X-User-Role", ETUDIANT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        String url = com.jayway.jsonpath.JsonPath.read(reponse, "$.data.url");

        mvc.perform(get(url).header("X-User-Id", SARA).header("X-User-Role", ETUDIANT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
    }

    @Test
    void unFauxFichierImageOuTropLourdEstRefuse() throws Exception {
        mvc.perform(multipart("/api/forum/pieces-jointes")
                        .file(new MockMultipartFile("fichier", "virus.png", "image/png", "<script>".getBytes()))
                        .header("X-User-Id", YASSINE).header("X-User-Role", ETUDIANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("PNG, JPEG et WebP")));
        byte[] lourd = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(lourd, (byte) 1);
        lourd[0] = (byte) 0xFF;
        lourd[1] = (byte) 0xD8;
        lourd[2] = (byte) 0xFF;
        mvc.perform(multipart("/api/forum/pieces-jointes")
                        .file(new MockMultipartFile("fichier", "photo.jpg", "image/jpeg", lourd))
                        .header("X-User-Id", YASSINE).header("X-User-Role", ETUDIANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("2 Mo")));
    }

    private org.springframework.test.web.servlet.ResultActions liste(long user, String param, String valeur)
            throws Exception {
        return en(user, role(user), get("/api/forum/discussions").param(param, valeur));
    }
}

package tn.esprit.bawsla.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import jakarta.persistence.PersistenceException;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Etiquette;
import tn.esprit.bawsla.forum.entity.JournalModeration;
import tn.esprit.bawsla.forum.entity.ParametresForumCours;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.Vote;
import tn.esprit.bawsla.forum.entity.Vue;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;

/** Le schéma de bawsla_forum se crée (H2 en mode MySQL) et les règles portées par le modèle tiennent. */
@DataJpaTest
class ModeleDonneesTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void uneDiscussionGardeSesEtiquettesSonVecteurEtSesValeursParDefaut() {
        Etiquette jointures = new Etiquette();
        jointures.setLibelle("jointures");
        em.persist(jointures);

        Discussion d = discussion("Pourquoi mon LEFT JOIN renvoie autant de lignes ?");
        d.getEtiquettes().add(jointures);
        d.setEmbedding(new float[] {0.6f, -0.8f, 0f});
        Long id = em.persistAndFlush(d).getId();
        em.clear();

        Discussion relue = em.find(Discussion.class, id);
        assertThat(relue.getStatut()).isEqualTo(StatutDiscussion.OUVERTE);
        assertThat(relue.getCreeLe()).isNotNull();
        assertThat(relue.getDerniereActiviteLe()).isEqualTo(relue.getCreeLe());
        assertThat(relue.getEmbedding()).containsExactly(0.6f, -0.8f, 0f);
        assertThat(relue.getEtiquettes()).extracting(Etiquette::getLibelle).containsExactly("jointures");
    }

    @Test
    void unUtilisateurNeVoteQuUneFoisParCible() {
        em.persistAndFlush(vote(7L, 1));
        assertThatThrownBy(() -> em.persistAndFlush(vote(7L, -1))).isInstanceOf(PersistenceException.class);
    }

    @Test
    void uneVueNeCompteQuUneFoisParJour() {
        em.persistAndFlush(vue(LocalDate.of(2026, 10, 15)));
        em.persistAndFlush(vue(LocalDate.of(2026, 10, 16)));
        assertThatThrownBy(() -> em.persistAndFlush(vue(LocalDate.of(2026, 10, 16))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void leJournalDeModerationNeSeModifiePas() {
        JournalModeration j = new JournalModeration();
        j.setActeurId(2L);
        j.setRole("ENSEIGNANT");
        j.setAction(ActionModeration.MASQUER);
        j.setCibleType("REPONSE");
        j.setCibleId(9L);
        j.setMotif("Partage de réponses d'examen");
        Long id = em.persistAndFlush(j).getId();

        j.setMotif("motif réécrit");
        em.flush();
        em.clear();

        assertThat(em.find(JournalModeration.class, id).getMotif()).isEqualTo("Partage de réponses d'examen");
    }

    @Test
    void lesReglagesDUnCoursOuvrentLeForumParDefaut() {
        ParametresForumCours p = new ParametresForumCours();
        p.setCoursId(5L);
        em.persistAndFlush(p);
        em.clear();

        ParametresForumCours relu = em.find(ParametresForumCours.class, 5L);
        assertThat(List.of(relu.isOuvert(), relu.isAnonymatAutorise(), relu.isVigieActive(),
                relu.isReponseGeneraleAutorisee())).containsOnly(true);
        assertThat(relu.isModeExamen()).isFalse();
    }

    @Test
    void uneReponseDeLaVigieCommenceEnAttente() {
        ReponseIa r = new ReponseIa();
        r.setCoursId(5L);
        r.setDiscussionId(1L);
        r.setNiveau(NiveauReponseIa.COURS);
        Long id = em.persistAndFlush(r).getId();
        em.clear();

        ReponseIa relue = em.find(ReponseIa.class, id);
        assertThat(relue.getStatut()).isEqualTo(StatutReponseIa.EN_ATTENTE);
        assertThat(relue.getConfiance()).isNull();
    }

    private static Discussion discussion(String titre) {
        Discussion d = new Discussion();
        d.setCoursId(5L);
        d.setAuteurId(3L);
        d.setType(TypeDiscussion.QUESTION);
        d.setTitre(titre);
        d.setContenu("Mon `LEFT JOIN` se comporte comme un `INNER JOIN`.");
        return d;
    }

    private static Vote vote(Long userId, int valeur) {
        Vote v = new Vote();
        v.setUserId(userId);
        v.setCibleType(CibleType.REPONSE);
        v.setCibleId(4L);
        v.setValeur(valeur);
        return v;
    }

    private static Vue vue(LocalDate jour) {
        Vue v = new Vue();
        v.setUserId(3L);
        v.setDiscussionId(1L);
        v.setJour(jour);
        return v;
    }
}

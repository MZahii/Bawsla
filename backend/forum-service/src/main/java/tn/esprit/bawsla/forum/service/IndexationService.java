package tn.esprit.bawsla.forum.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import tn.esprit.bawsla.forum.client.ChapitreDto;
import tn.esprit.bawsla.forum.client.CoursClient;
import tn.esprit.bawsla.forum.client.CoursTexteDto;
import tn.esprit.bawsla.forum.client.IndexerIaDto;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.config.TachesConfig;
import tn.esprit.bawsla.forum.dto.IndexCoursResponse;
import tn.esprit.bawsla.forum.entity.IndexCours;
import tn.esprit.bawsla.forum.entity.PassageCours;
import tn.esprit.bawsla.forum.entity.enums.StatutIndex;
import tn.esprit.bawsla.forum.evenement.DiscussionCreee;
import tn.esprit.bawsla.forum.repository.IndexCoursRepository;
import tn.esprit.bawsla.forum.repository.PassageCoursRepository;

/**
 * Indexation d'un cours pour le RAG (plan §4.2) : texte lu dans cours-service (contrat 2 et chapitres s'ils
 * existent), hash SHA-256, découpage et vectorisation par l'ai-service, passages stockés dans {@code passage_cours}.
 * <ul>
 *   <li>Rien n'est refait si le texte n'a pas changé (même hash), sauf réindexation forcée.</li>
 *   <li>Si le texte du cours est indisponible ou encore un stub : en profil dev, texte de démonstration
 *       ({@code demo/cours-demo.md}) ; sinon état ERREUR, et La Vigie s'appuie sur la base du forum.</li>
 *   <li>Lancée en tâche de fond : à la première question d'un cours, ou à la demande du formateur.</li>
 * </ul>
 */
@Service
public class IndexationService {

    private static final Logger log = LoggerFactory.getLogger(IndexationService.class);
    static final String RESSOURCE_DEMO = "demo/cours-demo.md";

    private final CoursClient coursClient;
    private final IaService ia;
    private final IndexCoursRepository indexRepository;
    private final PassageCoursRepository passageRepository;
    private final AccesService acces;
    private final IdentiteService identites;
    private final Executor executeur;
    private final TransactionTemplate transaction;
    private final boolean texteDemo;
    private final Clock horloge;

    public IndexationService(CoursClient coursClient, IaService ia, IndexCoursRepository indexRepository,
                             PassageCoursRepository passageRepository, AccesService acces, IdentiteService identites,
                             @Qualifier(TachesConfig.EXECUTEUR) Executor executeur,
                             PlatformTransactionManager transactions,
                             @Value("${forum.rag.texte-demo:false}") boolean texteDemo, Clock horloge) {
        this.coursClient = coursClient;
        this.ia = ia;
        this.indexRepository = indexRepository;
        this.passageRepository = passageRepository;
        this.acces = acces;
        this.identites = identites;
        this.executeur = executeur;
        this.transaction = Transactions.nouvelle(transactions);
        this.texteDemo = texteDemo;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ API (formateur du cours ou admin)

    public IndexCoursResponse etat(Long coursId, CurrentUser user) {
        acces.exigerModerateur(acces.contexte(user, coursId));
        return indexRepository.findById(coursId).map(IndexCoursResponse::from)
                .orElse(new IndexCoursResponse(coursId, null, 0, null, null, false, null));
    }

    /** Réindexation forcée, en tâche de fond ; renvoie l'état « en cours ». */
    public IndexCoursResponse reindexer(Long coursId, CurrentUser user) {
        acces.exigerModerateur(acces.contexte(user, coursId));
        marquerEnCours(coursId);
        executeur.execute(identites.avecIdentite(() -> indexer(coursId, true)));
        return etat(coursId, user);
    }

    /** Réindexe en tâche de fond tous les cours déjà indexés (admin) ; renvoie leur nombre. */
    public int reindexerTout(CurrentUser user) {
        acces.exigerAdmin(user);
        List<Long> cours = indexRepository.findAll().stream().map(IndexCours::getCoursId).toList();
        cours.forEach(id -> executeur.execute(identites.avecIdentite(() -> indexer(id, true))));
        return cours.size();
    }

    /** Première discussion d'un cours jamais indexé : on lance l'indexation pour que La Vigie puisse répondre. */
    @TransactionalEventListener
    public void discussionCreee(DiscussionCreee e) {
        if (!indexRepository.existsById(e.coursId())) {
            executeur.execute(identites.avecIdentite(() -> indexer(e.coursId(), false)));
        }
    }

    // ------------------------------------------------------------------ Indexation

    /** Indexe le cours ; ne lève jamais : un échec est enregistré dans l'état de l'index (statut ERREUR). */
    public void indexer(Long coursId, boolean forcer) {
        try {
            Source source = lireSource(coursId);
            if (source == null) {
                enregistrerErreur(coursId, "Texte du cours indisponible : La Vigie s'appuie sur la base du forum");
                return;
            }
            IndexCours actuel = indexRepository.findById(coursId).orElse(null);
            if (!forcer && actuel != null && actuel.getStatut() == StatutIndex.A_JOUR
                    && source.hash().equals(actuel.getHashSource())) {
                return;
            }
            marquerEnCours(coursId);
            IndexerIaDto.Reponse reponse = ia.indexer(new IndexerIaDto.Requete(coursId, source.texte(), List.of(),
                    source.chapitres()));
            transaction.executeWithoutResult(s -> remplacer(coursId, source, reponse));
            log.info("Cours {} indexé : {} passages ({})", coursId, reponse.passages().size(), reponse.modele());
        } catch (RuntimeException e) {
            log.warn("Indexation du cours {} impossible : {}", coursId, e.getMessage());
            enregistrerErreur(coursId, e.getMessage());
        }
    }

    private void remplacer(Long coursId, Source source, IndexerIaDto.Reponse reponse) {
        passageRepository.supprimerDuCours(coursId);
        for (IndexerIaDto.Passage p : reponse.passages()) {
            PassageCours passage = new PassageCours();
            passage.setCoursId(coursId);
            passage.setChapitreId(p.chapitreId());
            passage.setPage(p.page());
            passage.setOrdre(p.ordre());
            passage.setTitreSection(p.titreSection() == null ? null : tronquer(p.titreSection(), 200));
            passage.setTexte(p.texte());
            passage.setEmbedding(p.vecteur());
            passage.setModeleEmbedding(reponse.modele());
            passageRepository.save(passage);
        }
        IndexCours index = indexRepository.findById(coursId).orElseGet(IndexCours::new);
        index.setCoursId(coursId);
        index.setHashSource(source.hash());
        index.setNbPassages(reponse.passages().size());
        index.setModeleEmbedding(reponse.modele());
        index.setStatut(StatutIndex.A_JOUR);
        index.setSourceDemo(source.demo());
        index.setIndexeLe(LocalDateTime.now(horloge));
        index.setErreur(null);
        indexRepository.save(index);
    }

    private void marquerEnCours(Long coursId) {
        transaction.executeWithoutResult(s -> {
            IndexCours index = indexRepository.findById(coursId).orElseGet(IndexCours::new);
            index.setCoursId(coursId);
            index.setStatut(StatutIndex.EN_COURS);
            indexRepository.save(index);
        });
    }

    private void enregistrerErreur(Long coursId, String erreur) {
        transaction.executeWithoutResult(s -> {
            IndexCours index = indexRepository.findById(coursId).orElseGet(IndexCours::new);
            index.setCoursId(coursId);
            index.setStatut(StatutIndex.ERREUR);
            index.setErreur(erreur == null ? "Erreur inconnue" : tronquer(erreur, 500));
            indexRepository.save(index);
        });
    }

    // ------------------------------------------------------------------ Source du texte

    /** Texte à indexer, son hash et son origine ; null si aucun texte n'est disponible. */
    record Source(String texte, List<IndexerIaDto.Chapitre> chapitres, String hash, boolean demo) {
    }

    Source lireSource(Long coursId) {
        String texte = null;
        try {
            ApiResponse<CoursTexteDto> r = coursClient.getTexte(coursId);
            CoursTexteDto dto = r == null ? null : r.data();
            if (dto != null && !dto.stub() && dto.texte() != null && !dto.texte().isBlank()) {
                texte = dto.texte();
            }
        } catch (RuntimeException e) {
            log.info("Texte du cours {} indisponible : {}", coursId, e.getMessage());
        }
        List<IndexerIaDto.Chapitre> chapitres = List.of();
        try {
            ApiResponse<List<ChapitreDto>> r = coursClient.getChapitres(coursId);
            if (r != null && r.data() != null) {
                chapitres = r.data().stream().filter(c -> c.contenu() != null && !c.contenu().isBlank())
                        .map(c -> new IndexerIaDto.Chapitre(c.id(), c.titre(), c.contenu())).toList();
            }
        } catch (RuntimeException e) {
            log.debug("Chapitres du cours {} indisponibles : {}", coursId, e.getMessage());
        }
        boolean demo = false;
        if (texte == null && chapitres.isEmpty()) {
            if (!texteDemo) {
                return null;
            }
            texte = lireDemo();
            demo = true;
        }
        StringBuilder empreinte = new StringBuilder(texte == null ? "" : texte);
        chapitres.forEach(c -> empreinte.append('\u0000').append(c.id()).append(c.titre()).append(c.contenu()));
        return new Source(texte, chapitres, sha256(empreinte.toString()), demo);
    }

    private static String lireDemo() {
        try (InputStream flux = new ClassPathResource(RESSOURCE_DEMO).getInputStream()) {
            return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Texte de démonstration introuvable", e);
        }
    }

    static String sha256(String texte) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tronquer(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}

package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tn.esprit.bawsla.forum.client.IaClient;
import tn.esprit.bawsla.forum.client.IndexerIaDto;
import tn.esprit.bawsla.forum.client.ReformulerIaDto;
import tn.esprit.bawsla.forum.client.RepondreIaDto;
import tn.esprit.bawsla.forum.client.VectoriserIaDto;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.forum.service.CacheTemporaire.Calcul;

/**
 * Accès à l'IA du forum (ai-service via la gateway), avec des replis qui ne cassent jamais le forum :
 * sans vectorisation la recherche passe en mots-clés, sans reformulation on cherche la question telle quelle.
 * Les requêtes répétées (saisie du titre, anti-rebond) sont mises en cache 10 minutes.
 */
@Service
public class IaService {

    private static final Logger log = LoggerFactory.getLogger(IaService.class);
    private static final Duration DUREE_CACHE = Duration.ofMinutes(10);
    private static final Duration DUREE_ECHEC = Duration.ofSeconds(20);

    /** Vecteur normalisé et modèle qui l'a produit (on ne compare jamais deux modèles différents). */
    public record Vecteur(float[] valeurs, String modele) {
    }

    /** Vecteurs de plusieurs textes, dans l'ordre, et leur modèle. */
    public record Vecteurs(List<float[]> valeurs, String modele) {
    }

    private final IaClient ia;
    private final IdentiteService identites;
    private final CacheTemporaire<String, Optional<Vecteur>> requetes;
    private final CacheTemporaire<String, String> reformulations;

    public IaService(IaClient ia, IdentiteService identites, Clock horloge) {
        this.ia = ia;
        this.identites = identites;
        this.requetes = new CacheTemporaire<>(horloge);
        this.reformulations = new CacheTemporaire<>(horloge);
    }

    /** Question reformulée en requête technique autonome en français ; la question elle-même en cas d'échec. */
    public String reformuler(String question, List<ReformulerIaDto.Message> historique) {
        if (historique != null && !historique.isEmpty()) {
            return appelReformuler(question, historique).valeur();
        }
        return reformulations.obtenir(question.trim(), q -> appelReformuler(q, List.of()));
    }

    private Calcul<String> appelReformuler(String question, List<ReformulerIaDto.Message> historique) {
        try {
            ReformulerIaDto.Reponse r = donnees(ia.reformuler(identites.jeton(),
                    new ReformulerIaDto.Requete(question, historique)));
            if (r != null && r.requete() != null && !r.requete().isBlank()) {
                return new Calcul<>(r.requete(), r.repli() ? DUREE_ECHEC : DUREE_CACHE);
            }
        } catch (RuntimeException e) {
            log.warn("Reformulation indisponible : {}", e.getMessage());
        }
        return new Calcul<>(question, DUREE_ECHEC);
    }

    /** Vecteur d'une requête de recherche (tâche REQUETE), ou vide si la vectorisation est indisponible. */
    public Optional<Vecteur> vectoriserRequete(String texte) {
        return requetes.obtenir(texte.trim(), t -> {
            Optional<Vecteurs> v = vectoriser(List.of(t), "REQUETE");
            return new Calcul<>(v.map(x -> new Vecteur(x.valeurs().get(0), x.modele())),
                    v.isPresent() ? DUREE_CACHE : DUREE_ECHEC);
        });
    }

    /** Vecteurs de documents (discussions, réponses validées), ou vide si indisponible. */
    public Optional<Vecteurs> vectoriserDocuments(List<String> textes) {
        return vectoriser(textes, "DOCUMENT");
    }

    private Optional<Vecteurs> vectoriser(List<String> textes, String tache) {
        try {
            VectoriserIaDto.Reponse r = donnees(ia.vectoriser(identites.jeton(),
                    new VectoriserIaDto.Requete(textes, tache)));
            if (r != null && r.vecteurs() != null && r.vecteurs().size() == textes.size() && r.modele() != null) {
                return Optional.of(new Vecteurs(r.vecteurs(), r.modele()));
            }
        } catch (RuntimeException e) {
            log.warn("Vectorisation indisponible : {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** Indexation d'un cours : pas de repli possible, l'appelant enregistre l'échec dans l'état de l'index. */
    public IndexerIaDto.Reponse indexer(IndexerIaDto.Requete requete) {
        try {
            IndexerIaDto.Reponse r = donnees(ia.indexer(identites.jeton(), requete));
            if (r == null || r.passages() == null) {
                throw new ServiceUnavailableException("Réponse d'indexation vide");
            }
            return r;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("Le service d'IA est indisponible : " + e.getMessage());
        }
    }

    /** Réponse sourcée de La Vigie ; lève ServiceUnavailableException si l'IA ne répond pas. */
    public RepondreIaDto.Reponse repondre(RepondreIaDto.Requete requete) {
        return obligatoire(() -> donnees(ia.repondre(identites.jeton(), requete)), "Réponse de La Vigie vide");
    }

    /** Réponse générale (niveau 3) ; lève ServiceUnavailableException si l'IA ne répond pas. */
    public RepondreIaDto.ReponseGenerale repondreGeneral(String question) {
        return obligatoire(() -> donnees(ia.repondreGeneral(identites.jeton(),
                new RepondreIaDto.RequeteGenerale(question))), "Réponse générale vide");
    }

    private static <T> T obligatoire(Supplier<T> appel, String siVide) {
        try {
            T r = appel.get();
            if (r == null) {
                throw new ServiceUnavailableException(siVide);
            }
            return r;
        } catch (ServiceUnavailableException | BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("Le service d'IA est indisponible : " + e.getMessage());
        }
    }

    public void oublierCaches() {
        requetes.vider();
        reformulations.vider();
    }

    private static <T> T donnees(ApiResponse<T> reponse) {
        return reponse == null ? null : reponse.data();
    }
}

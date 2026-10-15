package tn.esprit.bawsla.forum.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import tn.esprit.bawsla.forum.dto.SimilaireResponse;
import tn.esprit.bawsla.forum.dto.SourceVigie;
import tn.esprit.bawsla.forum.dto.VigieResponse;
import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;

/**
 * Passe d'une {@link ReponseIa} à sa vue API selon le lecteur.
 * <ul>
 *   <li>Apprenant : jamais une réponse rejetée ; le texte corrigé remplace l'original ; en mode examen, le texte
 *       complet est caché (indices seulement).</li>
 *   <li>Formateur du cours et admin : tout, y compris l'original d'une réponse corrigée.</li>
 *   <li>Confiance seulement pour une réponse sourcée (jamais pour la réponse générale ni l'abstention).</li>
 * </ul>
 * Stockage : {@code sourcesJson} = liste de sources ; {@code indicesJson} = liste d'indices, ou pour une réponse
 * générale l'objet {@code {"incertitudes": [...], "aVerifier": [...]}}.
 */
@Component
public class VigieMapper {

    public static final String AVERTISSEMENT_FORUM =
            "Le texte du cours n'est pas disponible : réponse tirée des réponses validées du forum.";
    public static final String AVERTISSEMENT_GENERAL =
            "Réponse générale, hors cours, non vérifiée : à confirmer avec ton formateur.";
    public static final String AVERTISSEMENT_EXAMEN =
            "Mode examen : La Vigie te guide avec des indices, sans donner la solution complète.";

    private static final TypeReference<List<SourceVigie>> SOURCES = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> TEXTES = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, List<String>>> GENERAL = new TypeReference<>() {
    };

    private final ObjectMapper json;

    public VigieMapper(ObjectMapper json) {
        this.json = json;
    }

    /** Vue de la réponse, ou null si ce lecteur ne doit pas la voir (réponse rejetée, pour un apprenant). */
    public VigieResponse vers(ReponseIa r, boolean moderateur, boolean modeExamen, Boolean monAvis,
                              List<SimilaireResponse> similaires) {
        if (r == null || (!moderateur && r.getStatut() == StatutReponseIa.REJETEE)) {
            return null;
        }
        boolean general = r.getNiveau() == NiveauReponseIa.GENERAL;
        boolean sourcee = !general && (r.getStatut() == StatutReponseIa.GENEREE
                || r.getStatut() == StatutReponseIa.VALIDEE || r.getStatut() == StatutReponseIa.CORRIGEE);
        String texte = r.getTexte();
        String texteCorrige = r.getTexteCorrige();
        if (!moderateur) {
            texte = texteCorrige != null ? texteCorrige : texte;
            texteCorrige = null;
            if (modeExamen && !general && sourcee) {
                texte = null;
            }
        }
        List<String> incertitudes = List.of();
        List<String> aVerifier = List.of();
        List<String> indices = List.of();
        if (general) {
            Map<String, List<String>> g = lire(r.getIndicesJson(), GENERAL, Map.of());
            incertitudes = g.getOrDefault("incertitudes", List.of());
            aVerifier = g.getOrDefault("aVerifier", List.of());
        } else {
            indices = lire(r.getIndicesJson(), TEXTES, List.of());
        }
        Integer confiance = sourcee ? r.getConfiance() : null;
        return new VigieResponse(r.getId(), r.getDiscussionId(), r.getConversationId(), r.getNiveau(),
                r.getStatut(), r.getMode(), texte, texteCorrige, lire(r.getSourcesJson(), SOURCES, List.of()), indices,
                incertitudes, aVerifier, confiance, niveauConfiance(confiance),
                r.getStatut() == StatutReponseIa.VALIDEE || r.getStatut() == StatutReponseIa.CORRIGEE,
                r.getUtileOui(), r.getUtileNon(), monAvis, similaires == null ? List.of() : similaires,
                avertissement(r, general, modeExamen && !moderateur), r.getVersionPrompt(), r.getFournisseur(),
                r.getCreeLe());
    }

    public String ecrire(Object valeur) {
        try {
            return valeur == null ? null : json.writeValueAsString(valeur);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation impossible", e);
        }
    }

    static String niveauConfiance(Integer confiance) {
        if (confiance == null) {
            return null;
        }
        return confiance >= 75 ? "ELEVEE" : confiance >= 50 ? "MOYENNE" : "FAIBLE";
    }

    private static String avertissement(ReponseIa r, boolean general, boolean examen) {
        if (general) {
            return AVERTISSEMENT_GENERAL;
        }
        if (examen) {
            return AVERTISSEMENT_EXAMEN;
        }
        return r.getNiveau() == NiveauReponseIa.FORUM && r.getStatut() != StatutReponseIa.HORS_COURS
                ? AVERTISSEMENT_FORUM : null;
    }

    private <T> T lire(String valeur, TypeReference<T> type, T defaut) {
        if (valeur == null || valeur.isBlank()) {
            return defaut;
        }
        try {
            return json.readValue(valeur, type);
        } catch (JsonProcessingException e) {
            return defaut;
        }
    }
}

package tn.esprit.bawsla.forum;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import tn.esprit.bawsla.forum.client.AidesIaDto;
import tn.esprit.bawsla.forum.client.IaClient;
import tn.esprit.bawsla.forum.client.IndexerIaDto;
import tn.esprit.bawsla.forum.client.ModererIaDto;
import tn.esprit.bawsla.forum.client.ReformulerIaDto;
import tn.esprit.bawsla.forum.client.RepondreIaDto;
import tn.esprit.bawsla.forum.client.VectoriserIaDto;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.forum.service.Similarite;

/**
 * Remplace l'ai-service (via la gateway) dans les tests : vecteurs déterministes par hachage des mots (deux textes
 * qui partagent des mots sont proches), reformulations fixes (derja → français), découpage simple par paragraphe.
 * La Vigie répond en citant mot pour mot le début du premier passage (confiance fixe {@link #CONFIANCE}) ; en mode
 * GUIDEE elle ajoute des indices. {@code horsCours} simule un modèle qui s'abstient malgré des passages.
 * {@code panne} simule une IA indisponible ; les corps reçus sont gardés pour vérifier qu'aucun identifiant ne part.
 */
class FauxIa implements IaClient {

    static final String MODELE = "faux-hachage-64";
    static final int DIMENSION = 64;
    static final int CONFIANCE = 82;
    static final String GENERALE = "Kubernetes orchestre des conteneurs ; vérifie la documentation officielle.";

    volatile boolean panne;
    volatile boolean horsCours;
    final Map<String, String> reformulations = new java.util.concurrent.ConcurrentHashMap<>();
    final List<Object> corps = new CopyOnWriteArrayList<>();
    final List<String> jetons = new CopyOnWriteArrayList<>();
    final AtomicInteger indexations = new AtomicInteger();
    /** Modération : premier mot-clé contenu dans le message → verdict ; sinon OK. */
    final Map<String, ModererIaDto.Reponse> moderations = new java.util.concurrent.ConcurrentSkipListMap<>();

    void remettre() {
        panne = false;
        horsCours = false;
        reformulations.clear();
        corps.clear();
        jetons.clear();
        indexations.set(0);
        moderations.clear();
    }

    @Override
    public ApiResponse<VectoriserIaDto.Reponse> vectoriser(String authorization, VectoriserIaDto.Requete requete) {
        appel(authorization, requete);
        List<float[]> vecteurs = requete.textes().stream().map(FauxIa::vecteur).toList();
        return ApiResponse.ok(new VectoriserIaDto.Reponse(vecteurs, MODELE, DIMENSION, "faux"));
    }

    @Override
    public ApiResponse<IndexerIaDto.Reponse> indexer(String authorization, IndexerIaDto.Requete requete) {
        appel(authorization, requete);
        indexations.incrementAndGet();
        List<IndexerIaDto.Passage> passages = new ArrayList<>();
        String titre = null;
        for (String bloc : (requete.texte() == null ? "" : requete.texte()).split("\n\\s*\n")) {
            String b = bloc.strip();
            if (b.startsWith("#")) {
                titre = b.replaceFirst("^#+\\s*", "");
            } else if (!b.isEmpty()) {
                passages.add(new IndexerIaDto.Passage(null, null, passages.size(), titre, b, vecteur(b)));
            }
        }
        for (IndexerIaDto.Chapitre c : requete.chapitres()) {
            passages.add(new IndexerIaDto.Passage(c.id(), null, passages.size(), c.titre(), c.contenu(),
                    vecteur(c.contenu())));
        }
        return ApiResponse.ok(new IndexerIaDto.Reponse(passages, MODELE, DIMENSION, "faux", 0));
    }

    @Override
    public ApiResponse<ReformulerIaDto.Reponse> reformuler(String authorization, ReformulerIaDto.Requete requete) {
        appel(authorization, requete);
        String requeteTechnique = reformulations.getOrDefault(requete.question(), requete.question());
        return ApiResponse.ok(new ReformulerIaDto.Reponse(requeteTechnique, "fr", "v3", "faux", false));
    }

    @Override
    public ApiResponse<RepondreIaDto.Reponse> repondre(String authorization, RepondreIaDto.Requete requete) {
        appel(authorization, requete);
        if (horsCours || requete.passages().isEmpty()) {
            return ApiResponse.ok(new RepondreIaDto.Reponse("", List.of(), 0, true, List.of(), "v3", "faux",
                    List.of()));
        }
        RepondreIaDto.Passage p = requete.passages().get(0);
        String citation = p.texte().length() > 60 ? p.texte().substring(0, 60) : p.texte();
        RepondreIaDto.Source source = new RepondreIaDto.Source(1, p.id(), p.origine(), p.cours(), p.texte(),
                p.chapitre(), p.page(), citation);
        List<String> indices = "GUIDEE".equals(requete.mode())
                ? List.of("Relis la définition [1]", "Observe les lignes sans correspondance [1]") : List.of();
        return ApiResponse.ok(new RepondreIaDto.Reponse("D'après le cours : " + citation + " [1].", List.of(source),
                CONFIANCE, false, indices, "v3", "faux", List.of()));
    }

    @Override
    public ApiResponse<RepondreIaDto.ReponseGenerale> repondreGeneral(String authorization,
                                                                      RepondreIaDto.RequeteGenerale requete) {
        appel(authorization, requete);
        return ApiResponse.ok(new RepondreIaDto.ReponseGenerale(GENERALE, List.of("Les versions évoluent vite"),
                List.of("La documentation officielle"), "v3", "faux"));
    }

    @Override
    public ApiResponse<ModererIaDto.Reponse> moderer(String authorization, ModererIaDto.Requete requete) {
        appel(authorization, requete);
        String texte = (requete.titre() == null ? "" : requete.titre()) + " " + requete.contenu();
        return ApiResponse.ok(moderations.entrySet().stream().filter(e -> texte.contains(e.getKey()))
                .map(Map.Entry::getValue).findFirst()
                .orElse(new ModererIaDto.Reponse("OK", 0, "Rien à signaler", null, "v3", "faux")));
    }

    static ModererIaDto.Reponse verdict(String categorie, double score) {
        return new ModererIaDto.Reponse(categorie, score, "Détecté par le test", null, "v3", "faux");
    }

    @Override
    public ApiResponse<AidesIaDto.ResumerReponse> resumer(String authorization, AidesIaDto.ResumerRequete requete) {
        appel(authorization, requete);
        return ApiResponse.ok(new AidesIaDto.ResumerReponse(List.of("Problème : " + requete.titre(),
                "Messages lus : " + requete.messages().size(), "Solution : condition dans le ON"), "v2", "faux"));
    }

    @Override
    public ApiResponse<AidesIaDto.AmeliorerReponse> ameliorer(String authorization,
                                                             AidesIaDto.AmeliorerRequete requete) {
        appel(authorization, requete);
        return ApiResponse.ok(new AidesIaDto.AmeliorerReponse("Pourquoi mon LEFT JOIN perd-il des lignes ?",
                List.of("Ajoute ta requête."), requete.etiquettesExistantes().stream().limit(1).toList(), false, "v2",
                "faux"));
    }

    /** Nom de chaque groupe : « Thème » suivi du premier mot du premier titre (déterministe). */
    @Override
    public ApiResponse<AidesIaDto.NommerReponse> nommerGroupes(String authorization, AidesIaDto.NommerRequete requete) {
        appel(authorization, requete);
        List<String> noms = new ArrayList<>();
        for (int i = 0; i < requete.groupes().size(); i++) {
            noms.add("Thème " + (i + 1) + " : " + requete.groupes().get(i).titres().get(0).split(" ")[0]);
        }
        return ApiResponse.ok(new AidesIaDto.NommerReponse(noms, false, "v2", "faux"));
    }

    private void appel(String authorization, Object requete) {
        if (panne) {
            throw new ServiceUnavailableException("IA en panne (test)");
        }
        jetons.add(authorization);
        corps.add(requete);
    }

    /** Vecteur normalisé : chaque mot significatif ajoute ±1 dans une case choisie par son empreinte. */
    static float[] vecteur(String texte) {
        float[] v = new float[DIMENSION];
        for (String mot : Similarite.mots(texte)) {
            int h = java.util.Arrays.hashCode(mot.getBytes(StandardCharsets.UTF_8));
            v[Math.floorMod(h, DIMENSION)] += (h & 1) == 0 ? 1 : -1;
        }
        double norme = 0;
        for (float x : v) {
            norme += x * x;
        }
        if (norme > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) Math.sqrt(norme);
            }
        }
        return v;
    }
}

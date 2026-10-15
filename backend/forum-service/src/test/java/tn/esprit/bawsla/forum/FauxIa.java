package tn.esprit.bawsla.forum;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import tn.esprit.bawsla.forum.client.IaClient;
import tn.esprit.bawsla.forum.client.IndexerIaDto;
import tn.esprit.bawsla.forum.client.ReformulerIaDto;
import tn.esprit.bawsla.forum.client.VectoriserIaDto;
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.forum.service.Similarite;

/**
 * Remplace l'ai-service (via la gateway) dans les tests : vecteurs déterministes par hachage des mots (deux textes
 * qui partagent des mots sont proches), reformulations fixes (derja → français), découpage simple par paragraphe.
 * {@code panne} simule une IA indisponible ; les corps reçus sont gardés pour vérifier qu'aucun identifiant ne part.
 */
class FauxIa implements IaClient {

    static final String MODELE = "faux-hachage-64";
    static final int DIMENSION = 64;

    volatile boolean panne;
    final Map<String, String> reformulations = new java.util.concurrent.ConcurrentHashMap<>();
    final List<Object> corps = new CopyOnWriteArrayList<>();
    final List<String> jetons = new CopyOnWriteArrayList<>();
    final AtomicInteger indexations = new AtomicInteger();

    void remettre() {
        panne = false;
        reformulations.clear();
        corps.clear();
        jetons.clear();
        indexations.set(0);
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

package tn.esprit.bawsla.forum.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.client.ReformulerIaDto;
import tn.esprit.bawsla.forum.entity.PassageCours;
import tn.esprit.bawsla.forum.entity.PassageForum;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.repository.PassageCoursRepository;
import tn.esprit.bawsla.forum.repository.PassageForumRepository;
import tn.esprit.bawsla.forum.service.IaService.Vecteur;
import tn.esprit.bawsla.forum.service.Similarite.Score;

/**
 * Récupération des passages pour La Vigie (plan §4.2 étape 2, §4.9) : deux sources, le texte du cours
 * ({@code passage_cours}) et les réponses validées par un humain ({@code passage_forum}).
 * Question reformulée (ou rendue autonome dans l'assistant), puis cosinus (top 20) et mots (top 20) fusionnés par RRF,
 * bonus de 20 % pour le cours, top 4. Si l'IA ne vectorise pas, seule la partie lexicale sert.
 */
@Service
@Transactional(readOnly = true)
public class RagService {

    static final int CANDIDATS = 20;
    static final int RETENUS = 4;
    static final double BONUS_COURS = 1.2;

    /** Passage retenu ; score = cosinus avec la question (ou score de mots sans vecteur), entre 0 et 1. */
    public record PassageRetenu(NiveauReponseIa origine, Long id, Long chapitreId, Integer page, String titreSection,
                                String texte, double score, Long discussionId) {
    }

    /** Passages retenus (dans l'ordre), meilleur score de pertinence et requête effectivement cherchée. */
    public record Recuperation(List<PassageRetenu> passages, double meilleurScore, boolean semantique,
                               boolean coursDisponible, String requete) {
    }

    private record Candidat(String cle, PassageRetenu passage, float[] vecteur, String modele) {
    }

    private final PassageCoursRepository passageCoursRepository;
    private final PassageForumRepository passageForumRepository;
    private final IaService ia;

    public RagService(PassageCoursRepository passageCoursRepository, PassageForumRepository passageForumRepository,
                      IaService ia) {
        this.passageCoursRepository = passageCoursRepository;
        this.passageForumRepository = passageForumRepository;
        this.ia = ia;
    }

    public Recuperation recuperer(Long coursId, String question, List<ReformulerIaDto.Message> historique) {
        String requete = ia.reformuler(question, historique);
        List<Candidat> candidats = new ArrayList<>();
        List<PassageCours> cours = passageCoursRepository.findByCoursIdOrderByOrdre(coursId);
        for (PassageCours p : cours) {
            candidats.add(new Candidat("C" + p.getId(), new PassageRetenu(NiveauReponseIa.COURS, p.getId(),
                    p.getChapitreId(), p.getPage(), p.getTitreSection(), p.getTexte(), 0, null),
                    p.getEmbedding(), p.getModeleEmbedding()));
        }
        for (PassageForum p : passageForumRepository.valides(coursId)) {
            candidats.add(new Candidat("F" + p.getId(), new PassageRetenu(NiveauReponseIa.FORUM, p.getId(), null,
                    null, null, p.getTexte(), 0, p.getDiscussionId()), p.getEmbedding(), p.getModeleEmbedding()));
        }
        if (candidats.isEmpty()) {
            return new Recuperation(List.of(), 0, false, false, requete);
        }

        Map<Long, Candidat> parIndex = new HashMap<>();
        for (int i = 0; i < candidats.size(); i++) {
            parIndex.put((long) i, candidats.get(i));
        }
        Set<String> mots = Similarite.mots(requete);
        Map<Long, Double> lexicaux = new HashMap<>();
        Map<Long, Double> cosinus = new HashMap<>();
        Optional<Vecteur> vecteur = ia.vectoriserRequete(requete);
        parIndex.forEach((i, c) -> {
            lexicaux.put(i, Similarite.lexical(mots, c.passage().titreSection(), c.passage().texte()));
            vecteur.filter(v -> c.vecteur() != null && v.modele().equals(c.modele()))
                    .ifPresent(v -> cosinus.put(i, Math.max(0, Similarite.cosinus(v.valeurs(), c.vecteur()))));
        });
        boolean semantique = !cosinus.isEmpty();
        List<Score> classementLexical = classement(lexicaux);
        List<Score> classementSemantique = classement(cosinus);
        List<Score> fusion = Similarite.rrf(List.of(classementLexical, classementSemantique)).stream()
                .map(s -> new Score(s.id(), s.score()
                        * (parIndex.get(s.id()).passage().origine() == NiveauReponseIa.COURS ? BONUS_COURS : 1)))
                .sorted(Comparator.comparingDouble(Score::score).reversed()).limit(RETENUS).toList();

        List<PassageRetenu> retenus = new ArrayList<>();
        double meilleur = 0;
        for (Score s : fusion) {
            double pertinence = semantique ? cosinus.getOrDefault(s.id(), 0.0) : lexicaux.getOrDefault(s.id(), 0.0);
            PassageRetenu p = parIndex.get(s.id()).passage();
            retenus.add(new PassageRetenu(p.origine(), p.id(), p.chapitreId(), p.page(), p.titreSection(), p.texte(),
                    Math.round(pertinence * 1000) / 1000.0, p.discussionId()));
            meilleur = Math.max(meilleur, pertinence);
        }
        return new Recuperation(retenus, meilleur, semantique, !cours.isEmpty(), requete);
    }

    private static List<Score> classement(Map<Long, Double> scores) {
        return scores.entrySet().stream().filter(e -> e.getValue() > 0)
                .map(e -> new Score(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(Score::score).reversed()).limit(CANDIDATS).toList();
    }
}

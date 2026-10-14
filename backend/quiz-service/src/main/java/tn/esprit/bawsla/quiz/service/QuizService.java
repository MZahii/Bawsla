package tn.esprit.bawsla.quiz.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import feign.FeignException;
import tn.esprit.bawsla.quiz.client.CoursClient;
import tn.esprit.bawsla.quiz.common.exception.BadRequestException;
import tn.esprit.bawsla.quiz.common.exception.ConflictException;
import tn.esprit.bawsla.quiz.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.quiz.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.quiz.common.security.CurrentUser;
import tn.esprit.bawsla.quiz.dto.PassageResponse;
import tn.esprit.bawsla.quiz.dto.QuizDetailResponse;
import tn.esprit.bawsla.quiz.dto.QuizRequest;
import tn.esprit.bawsla.quiz.dto.QuizRequest.QuestionRequest;
import tn.esprit.bawsla.quiz.dto.QuizResponse;
import tn.esprit.bawsla.quiz.dto.StatistiquesQuiz;
import tn.esprit.bawsla.quiz.dto.StatistiquesQuiz.QuestionStat;
import tn.esprit.bawsla.quiz.dto.StudioLigne;
import tn.esprit.bawsla.quiz.entity.Competence;
import tn.esprit.bawsla.quiz.entity.Option;
import tn.esprit.bawsla.quiz.entity.Question;
import tn.esprit.bawsla.quiz.entity.Quiz;
import tn.esprit.bawsla.quiz.entity.Reponse;
import tn.esprit.bawsla.quiz.entity.StatutQuiz;
import tn.esprit.bawsla.quiz.entity.Tentative;
import tn.esprit.bawsla.quiz.entity.TypeQuiz;
import tn.esprit.bawsla.quiz.repository.CompetenceRepository;
import tn.esprit.bawsla.quiz.repository.QuizRepository;
import tn.esprit.bawsla.quiz.repository.TentativeRepository;

/**
 * Quiz du formateur : création (saisie ou génération IA relue), édition, publication, statistiques ;
 * et vues de lecture selon le rôle (un apprenant ne voit que les quiz publiés, sans les bonnes réponses).
 */
@Service
@Transactional(readOnly = true)
public class QuizService {

    private final QuizRepository quizRepository;
    private final CompetenceRepository competenceRepository;
    private final TentativeRepository tentativeRepository;
    private final CoursClient coursClient;

    public QuizService(QuizRepository quizRepository, CompetenceRepository competenceRepository,
                       TentativeRepository tentativeRepository, CoursClient coursClient) {
        this.quizRepository = quizRepository;
        this.competenceRepository = competenceRepository;
        this.tentativeRepository = tentativeRepository;
        this.coursClient = coursClient;
    }

    /** Quiz visibles par l'utilisateur : les publiés, plus ses brouillons (tous pour l'admin). */
    public List<QuizResponse> findAll(Long coursId, TypeQuiz type, CurrentUser user) {
        List<Quiz> quiz = coursId == null
                ? quizRepository.findAllByOrderByDateCreationDesc()
                : quizRepository.findByCoursIdOrderByDateCreationDesc(coursId);
        return quiz.stream()
                .filter(q -> visible(q, user))
                .filter(q -> type == null || q.getType() == type)
                .map(QuizResponse::from)
                .toList();
    }

    public QuizResponse findById(Long id, CurrentUser user) {
        return QuizResponse.from(getVisible(id, user));
    }

    /** Vue éditeur, avec bonnes réponses et erreurs associées : propriétaire ou admin. */
    public QuizDetailResponse detail(Long id, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        user.requireOwnerOrAdmin(quiz.getEnseignantId());
        return QuizDetailResponse.from(quiz);
    }

    /** Vue de passage, sans aucune bonne réponse. Le formateur propriétaire peut prévisualiser un brouillon. */
    public PassageResponse passage(Long id, CurrentUser user) {
        Quiz quiz = getVisible(id, user);
        return PassageResponse.from(quiz, tentativeRepository.countByQuizIdAndEtudiantId(id, user.id()));
    }

    @Transactional
    public QuizResponse create(QuizRequest request, CurrentUser user) {
        user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        verifierCoursExiste(request.coursId());
        Quiz quiz = new Quiz();
        quiz.setEnseignantId(user.id());
        apply(quiz, request);
        return QuizResponse.from(quizRepository.save(quiz));
    }

    @Transactional
    public QuizResponse update(Long id, QuizRequest request, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        user.requireOwnerOrAdmin(quiz.getEnseignantId());
        if (request.questions() != null && tentativeRepository.countByQuizId(id) > 0) {
            throw new ConflictException("Des apprenants ont déjà passé ce quiz : ses questions ne peuvent plus changer. "
                    + "Crée un nouveau quiz.");
        }
        if (!quiz.getCoursId().equals(request.coursId())) {
            verifierCoursExiste(request.coursId());
        }
        apply(quiz, request);
        return QuizResponse.from(quizRepository.saveAndFlush(quiz));
    }

    /** Publication après relecture : le quiz devient visible des apprenants. */
    @Transactional
    public QuizResponse publier(Long id, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        user.requireOwnerOrAdmin(quiz.getEnseignantId());
        if (quiz.getQuestions().isEmpty()) {
            throw new BadRequestException("Ajoute au moins une question avant de publier");
        }
        quiz.setStatut(StatutQuiz.PUBLIE);
        quiz.setDatePublication(LocalDateTime.now());
        return QuizResponse.from(quiz);
    }

    @Transactional
    public QuizResponse depublier(Long id, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        user.requireOwnerOrAdmin(quiz.getEnseignantId());
        quiz.setStatut(StatutQuiz.BROUILLON);
        return QuizResponse.from(quiz);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        user.requireOwnerOrAdmin(quiz.getEnseignantId());
        if (tentativeRepository.countByQuizId(id) > 0) {
            throw new ConflictException("Des apprenants ont passé ce quiz : dépublie-le plutôt que de le supprimer");
        }
        quizRepository.delete(quiz);
    }

    /** Studio : les quiz du formateur (tous pour l'admin) avec leurs chiffres. */
    public List<StudioLigne> mesQuiz(CurrentUser user) {
        user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        List<Quiz> quiz = user.isAdmin()
                ? quizRepository.findAllByOrderByDateCreationDesc()
                : quizRepository.findByEnseignantIdOrderByDateCreationDesc(user.id());
        return quiz.stream().map(q -> {
            List<Tentative> tentatives = tentativeRepository.findByQuizId(q.getId());
            return new StudioLigne(q.getId(), q.getTitre(), q.getCoursId(), q.getType(), q.getStatut(),
                    q.getQuestions().size(), tentatives.size(), moyenne(tentatives), tauxReussite(tentatives),
                    q.getDateCreation());
        }).toList();
    }

    /** Distribution des scores et taux d'erreur par question, de la plus ratée à la moins ratée. */
    public StatistiquesQuiz statistiques(Long id, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        user.requireOwnerOrAdmin(quiz.getEnseignantId());
        List<Tentative> tentatives = tentativeRepository.findByQuizId(id);

        List<Integer> distribution = new ArrayList<>(List.of(0, 0, 0, 0, 0));
        tentatives.forEach(t -> {
            int tranche = Math.min(4, t.getPourcentage() / 20);
            distribution.set(tranche, distribution.get(tranche) + 1);
        });

        Map<Long, List<Reponse>> parQuestion = tentatives.stream()
                .flatMap(t -> t.getReponses().stream())
                .collect(Collectors.groupingBy(r -> r.getQuestion().getId()));
        List<QuestionStat> questions = new ArrayList<>();
        List<Question> liste = quiz.getQuestions();
        for (int i = 0; i < liste.size(); i++) {
            Question q = liste.get(i);
            List<Reponse> reponses = parQuestion.getOrDefault(q.getId(), List.of());
            long fausses = reponses.stream().filter(r -> !r.isCorrecte()).count();
            int taux = reponses.isEmpty() ? 0 : Math.round(fausses * 100f / reponses.size());
            questions.add(new QuestionStat(q.getId(), i + 1, q.getEnonce(),
                    q.getCompetence() == null ? null : q.getCompetence().getLibelle(), reponses.size(), taux));
        }
        questions.sort(Comparator.comparingInt(QuestionStat::tauxErreur).reversed());
        return new StatistiquesQuiz(id, tentatives.size(), moyenne(tentatives), tauxReussite(tentatives),
                distribution, questions);
    }

    Quiz getOrThrow(Long id) {
        return quizRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Quiz", id));
    }

    /** Un brouillon d'un autre formateur est traité comme inexistant. */
    private Quiz getVisible(Long id, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        if (!visible(quiz, user)) {
            throw new ResourceNotFoundException("Quiz", id);
        }
        return quiz;
    }

    private static boolean visible(Quiz q, CurrentUser user) {
        return q.getStatut() == StatutQuiz.PUBLIE || user.isAdmin() || user.id().equals(q.getEnseignantId());
    }

    private static Double moyenne(List<Tentative> tentatives) {
        return tentatives.isEmpty() ? null
                : arrondi(tentatives.stream().mapToInt(Tentative::getPourcentage).average().orElse(0));
    }

    private static Double tauxReussite(List<Tentative> tentatives) {
        return tentatives.isEmpty() ? null
                : arrondi(tentatives.stream().filter(Tentative::isReussie).count() * 100.0 / tentatives.size());
    }

    private static Double arrondi(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** Appel inter-services via Feign : le lien Quiz -> Cours se fait par identifiant uniquement. */
    private void verifierCoursExiste(Long coursId) {
        try {
            coursClient.getCours(coursId);
        } catch (FeignException.NotFound e) {
            throw new BadRequestException("Le cours " + coursId + " n'existe pas");
        } catch (FeignException e) {
            throw new ServiceUnavailableException("cours-service indisponible, réessayez plus tard");
        }
    }

    private void apply(Quiz quiz, QuizRequest r) {
        TypeQuiz type = r.type() != null ? r.type() : TypeQuiz.QUIZ;
        quiz.setTitre(r.titre().strip());
        quiz.setDescription(r.description());
        quiz.setCoursId(r.coursId());
        quiz.setType(type);
        quiz.setTheme(vide(r.theme()));
        quiz.setMatiere(vide(r.matiere()));
        quiz.setDifficulte(r.difficulte());
        quiz.setSeuilReussite(r.seuilReussite() != null ? r.seuilReussite() : 70);
        quiz.setTentativesMax(r.tentativesMax() != null ? r.tentativesMax() : type == TypeQuiz.EXAMEN ? 3 : 0);
        quiz.setDureeMinutes(r.dureeMinutes() != null ? r.dureeMinutes() : type == TypeQuiz.EXAMEN ? 45 : 15);
        if (r.questions() == null) {
            return;
        }
        quiz.getQuestions().clear();
        Map<String, Competence> competences = new HashMap<>();
        for (int i = 0; i < r.questions().size(); i++) {
            quiz.ajouterQuestion(question(r.questions().get(i), i + 1, r.coursId(), competences));
        }
    }

    private Question question(QuestionRequest r, int numero, Long coursId, Map<String, Competence> cache) {
        long bonnes = r.choix().stream().filter(QuizRequest.OptionRequest::estCorrecte).count();
        if (bonnes != 1) {
            throw new BadRequestException("Question " + numero + " : il faut exactement une bonne réponse");
        }
        Question q = new Question();
        q.setEnonce(r.enonce().strip());
        q.setNiveauBloom(r.niveauBloom());
        q.setExplication(vide(r.explication()));
        String libelle = vide(r.competence());
        if (libelle != null) {
            q.setCompetence(cache.computeIfAbsent(libelle, l -> competence(coursId, l)));
        }
        r.choix().forEach(c -> q.ajouterOption(new Option(c.texte().strip(), c.estCorrecte(),
                c.estCorrecte() ? null : vide(c.erreurAssociee()))));
        return q;
    }

    private Competence competence(Long coursId, String libelle) {
        return competenceRepository.findByCoursIdAndLibelle(coursId, libelle)
                .orElseGet(() -> competenceRepository.save(new Competence(libelle, coursId)));
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static <T> Map<Long, T> parId(List<T> liste, Function<T, Long> id) {
        return liste.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}

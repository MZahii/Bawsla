package tn.esprit.bawsla.quiz.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.quiz.common.exception.BadRequestException;
import tn.esprit.bawsla.quiz.common.exception.ConflictException;
import tn.esprit.bawsla.quiz.common.security.CurrentUser;
import tn.esprit.bawsla.quiz.dto.HistoriqueLigne;
import tn.esprit.bawsla.quiz.dto.NotionRateeDto;
import tn.esprit.bawsla.quiz.dto.TentativeLigne;
import tn.esprit.bawsla.quiz.dto.TentativeRequest;
import tn.esprit.bawsla.quiz.dto.TentativeRequest.ReponseRequest;
import tn.esprit.bawsla.quiz.dto.TentativeResultat;
import tn.esprit.bawsla.quiz.entity.NotionRatee;
import tn.esprit.bawsla.quiz.entity.Option;
import tn.esprit.bawsla.quiz.entity.Question;
import tn.esprit.bawsla.quiz.entity.Quiz;
import tn.esprit.bawsla.quiz.entity.Reponse;
import tn.esprit.bawsla.quiz.entity.StatutQuiz;
import tn.esprit.bawsla.quiz.entity.Tentative;
import tn.esprit.bawsla.quiz.entity.TypeQuiz;
import tn.esprit.bawsla.quiz.repository.CertificatRepository;
import tn.esprit.bawsla.quiz.repository.NotionRateeRepository;
import tn.esprit.bawsla.quiz.repository.ReponseRepository;
import tn.esprit.bawsla.quiz.repository.TentativeRepository;

/**
 * Passage d'un quiz : correction côté serveur (le front ne connaît jamais les bonnes réponses avant de soumettre),
 * enregistrement d'une NotionRatee par erreur, certificat si l'examen final est réussi.
 * Fournit aussi le contrat notions-ratees, calculé à partir de l'historique des réponses.
 */
@Service
@Transactional(readOnly = true)
public class TentativeService {

    private static final int LONGUEUR_NOTION = 150;

    private final QuizService quizService;
    private final TentativeRepository tentativeRepository;
    private final ReponseRepository reponseRepository;
    private final NotionRateeRepository notionRateeRepository;
    private final CertificatRepository certificatRepository;
    private final CertificatService certificatService;

    public TentativeService(QuizService quizService, TentativeRepository tentativeRepository,
                            ReponseRepository reponseRepository, NotionRateeRepository notionRateeRepository,
                            CertificatRepository certificatRepository, CertificatService certificatService) {
        this.quizService = quizService;
        this.tentativeRepository = tentativeRepository;
        this.reponseRepository = reponseRepository;
        this.notionRateeRepository = notionRateeRepository;
        this.certificatRepository = certificatRepository;
        this.certificatService = certificatService;
    }

    @Transactional
    public TentativeResultat soumettre(Long quizId, TentativeRequest request, CurrentUser user) {
        Quiz quiz = quizService.getOrThrow(quizId);
        if (quiz.getStatut() != StatutQuiz.PUBLIE) {
            throw new BadRequestException("Ce quiz n'est pas encore publié");
        }
        if (quiz.getTentativesMax() > 0
                && tentativeRepository.countByQuizIdAndEtudiantId(quizId, user.id()) >= quiz.getTentativesMax()) {
            throw new ConflictException("Tu as utilisé tes " + quiz.getTentativesMax() + " tentatives pour ce quiz");
        }
        if (quiz.getType() == TypeQuiz.EXAMEN && certificatRepository.existsByEtudiantIdAndCoursId(user.id(), quiz.getCoursId())) {
            throw new ConflictException("Tu as déjà obtenu le certificat de ce cours");
        }

        Map<Long, Long> choix = choixParQuestion(quiz, request);
        Tentative tentative = new Tentative(quiz, user.id());
        for (Question q : quiz.getQuestions()) {
            tentative.ajouterReponse(new Reponse(q, option(q, choix.get(q.getId()))));
        }
        tentative.corriger(quiz.getQuestions().size());
        tentativeRepository.save(tentative);

        // Diagnostic de base de chaque erreur : l'erreur de raisonnement portée par le distracteur choisi.
        // La remédiation IA l'enrichit à la demande (RemediationService).
        tentative.getReponses().stream().filter(r -> !r.isCorrecte()).forEach(r -> notionRateeRepository.save(
                new NotionRatee(r, user.id(), quiz.getCoursId(), notion(r.getQuestion()), r.diagnostic())));

        String certificat = quiz.getType() == TypeQuiz.EXAMEN && tentative.isReussie()
                ? certificatService.delivrer(user.id(), quiz, tentative.getPourcentage())
                : null;
        return TentativeResultat.from(tentative, certificat);
    }

    /** Historique de l'apprenant, du plus récent au plus ancien, comparé aux autres apprenants du même quiz. */
    public List<HistoriqueLigne> historique(CurrentUser user) {
        return tentativeRepository.findByEtudiantIdOrderByDateTentativeDesc(user.id()).stream().map(t -> {
            Double autres = tentativeRepository.moyenneDesAutres(t.getQuiz().getId(), user.id());
            return new HistoriqueLigne(t.getId(), t.getQuiz().getId(), t.getQuiz().getTitre(), t.getQuiz().getCoursId(),
                    t.getQuiz().getType(), t.getPourcentage(), t.isReussie(), t.getDateTentative(),
                    autres == null ? null : Math.round(autres * 10) / 10.0);
        }).toList();
    }

    /**
     * Contrat 6 (consommé par le module User pour le ML) : tentatives corrigées d'un apprenant, la plus récente d'abord.
     * L'apprenant lui-même, un ENSEIGNANT ou un ADMIN ; {@code etudiantId} absent : 400.
     */
    public List<TentativeLigne> tentativesDe(Long etudiantId, CurrentUser user) {
        if (etudiantId == null) {
            throw new BadRequestException("Le paramètre etudiantId est obligatoire.");
        }
        if (!user.id().equals(etudiantId)) {
            user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        }
        return tentativeRepository.findByEtudiantIdOrderByDateTentativeDesc(etudiantId).stream().map(TentativeLigne::from).toList();
    }

    /**
     * Contrat 3 (consommé par le module User pour le ML) : erreurs d'un apprenant agrégées par compétence.
     * tauxReussite = réponses justes / réponses, entre 0.0 et 1.0. Triées de la plus ratée à la moins ratée.
     */
    public List<NotionRateeDto> notionsRatees(Long etudiantId, CurrentUser user) {
        if (!user.id().equals(etudiantId)) {
            user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        }
        return reponseRepository.statistiquesParCompetence(etudiantId).stream()
                .filter(s -> s.nbErreurs() > 0)
                .map(s -> new NotionRateeDto(s.notion(), s.coursId(), s.nbErreurs().intValue(),
                        Math.round((1 - (double) s.nbErreurs() / s.nbReponses()) * 100) / 100.0))
                .sorted(Comparator.comparingInt(NotionRateeDto::nbErreurs).reversed()
                        .thenComparingDouble(NotionRateeDto::tauxReussite))
                .toList();
    }

    /** Vérifie que chaque réponse vise une question du quiz, une seule fois, avec un choix de cette question. */
    private static Map<Long, Long> choixParQuestion(Quiz quiz, TentativeRequest request) {
        Map<Long, Question> questions = QuizService.parId(quiz.getQuestions(), Question::getId);
        Map<Long, Long> choix = new HashMap<>();
        for (ReponseRequest r : request.reponses()) {
            Question q = questions.get(r.questionId());
            if (q == null) {
                throw new BadRequestException("La question " + r.questionId() + " n'appartient pas à ce quiz");
            }
            if (choix.containsKey(r.questionId())) {
                throw new BadRequestException("La question " + r.questionId() + " a reçu deux réponses");
            }
            if (r.optionId() != null && q.getOptions().stream().noneMatch(o -> o.getId().equals(r.optionId()))) {
                throw new BadRequestException("Le choix " + r.optionId() + " n'appartient pas à la question " + r.questionId());
            }
            choix.put(r.questionId(), r.optionId());
        }
        return choix;
    }

    private static Option option(Question q, Long optionId) {
        return optionId == null ? null
                : q.getOptions().stream().filter(o -> o.getId().equals(optionId)).findFirst().orElse(null);
    }

    /** La compétence de la question, sinon le thème ou le titre du quiz. */
    static String notion(Question q) {
        String notion = q.getCompetence() != null ? q.getCompetence().getLibelle()
                : q.getQuiz().getTheme() != null ? q.getQuiz().getTheme() : q.getQuiz().getTitre();
        return notion.length() > LONGUEUR_NOTION ? notion.substring(0, LONGUEUR_NOTION) : notion;
    }
}

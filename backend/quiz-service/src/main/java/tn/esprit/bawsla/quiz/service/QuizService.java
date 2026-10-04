package tn.esprit.bawsla.quiz.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import feign.FeignException;
import tn.esprit.bawsla.quiz.client.CoursClient;
import tn.esprit.bawsla.quiz.common.exception.BadRequestException;
import tn.esprit.bawsla.quiz.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.quiz.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.quiz.common.security.CurrentUser;
import tn.esprit.bawsla.quiz.dto.NotionRateeDto;
import tn.esprit.bawsla.quiz.dto.QuizRequest;
import tn.esprit.bawsla.quiz.dto.QuizResponse;
import tn.esprit.bawsla.quiz.entity.Quiz;
import tn.esprit.bawsla.quiz.repository.QuizRepository;

@Service
@Transactional(readOnly = true)
public class QuizService {

    private final QuizRepository quizRepository;
    private final CoursClient coursClient;

    public QuizService(QuizRepository quizRepository, CoursClient coursClient) {
        this.quizRepository = quizRepository;
        this.coursClient = coursClient;
    }

    public List<QuizResponse> findAll(Long coursId) {
        List<Quiz> quiz = coursId == null
                ? quizRepository.findAllByOrderByDateCreationDesc()
                : quizRepository.findByCoursIdOrderByDateCreationDesc(coursId);
        return quiz.stream().map(QuizResponse::from).toList();
    }

    public QuizResponse findById(Long id) {
        return QuizResponse.from(getOrThrow(id));
    }

    @Transactional
    public QuizResponse create(QuizRequest request, CurrentUser user) {
        user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        verifierCoursExiste(request.coursId());
        Quiz quiz = new Quiz();
        apply(quiz, request);
        quiz.setEnseignantId(user.id());
        return QuizResponse.from(quizRepository.save(quiz));
    }

    @Transactional
    public QuizResponse update(Long id, QuizRequest request, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        user.requireOwnerOrAdmin(quiz.getEnseignantId());
        if (!quiz.getCoursId().equals(request.coursId())) {
            verifierCoursExiste(request.coursId());
        }
        apply(quiz, request);
        return QuizResponse.from(quiz);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        Quiz quiz = getOrThrow(id);
        user.requireOwnerOrAdmin(quiz.getEnseignantId());
        quizRepository.delete(quiz);
    }

    /**
     * STUB du contrat consommé par le module User (ML).
     * Sera calculé à partir de l'historique des tentatives quand il existera.
     */
    public List<NotionRateeDto> notionsRatees(Long etudiantId, CurrentUser user) {
        if (!user.id().equals(etudiantId)) {
            user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        }
        return List.of();
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

    private Quiz getOrThrow(Long id) {
        return quizRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Quiz", id));
    }

    private static void apply(Quiz quiz, QuizRequest r) {
        quiz.setTitre(r.titre());
        quiz.setDescription(r.description());
        quiz.setCoursId(r.coursId());
    }
}

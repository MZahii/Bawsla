package tn.esprit.bawsla.quiz.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.esprit.bawsla.quiz.common.ApiResponse;
import tn.esprit.bawsla.quiz.common.security.CurrentUser;
import tn.esprit.bawsla.quiz.dto.QuizDetailResponse;
import tn.esprit.bawsla.quiz.dto.QuizRequest;
import tn.esprit.bawsla.quiz.dto.QuizResponse;
import tn.esprit.bawsla.quiz.dto.StatistiquesQuiz;
import tn.esprit.bawsla.quiz.dto.StudioLigne;
import tn.esprit.bawsla.quiz.entity.TypeQuiz;
import tn.esprit.bawsla.quiz.service.QuizService;

/** Gestion des quiz par le formateur : brouillon, édition, publication, statistiques. */
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    /** Quiz visibles : publiés pour tous, plus les brouillons de leur auteur (et tout pour l'admin). */
    @GetMapping
    public ApiResponse<List<QuizResponse>> findAll(@RequestParam(required = false) Long coursId,
                                                   @RequestParam(required = false) TypeQuiz type, CurrentUser user) {
        return ApiResponse.ok(quizService.findAll(coursId, type, user));
    }

    /** Studio du formateur : ses quiz avec tentatives, moyenne et taux de réussite. */
    @GetMapping("/mes-quiz")
    public ApiResponse<List<StudioLigne>> mesQuiz(CurrentUser user) {
        return ApiResponse.ok(quizService.mesQuiz(user));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuizResponse> findById(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(quizService.findById(id, user));
    }

    /** Quiz complet avec les bonnes réponses : auteur ou admin uniquement. */
    @GetMapping("/{id}/detail")
    public ApiResponse<QuizDetailResponse> detail(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(quizService.detail(id, user));
    }

    @GetMapping("/{id}/statistiques")
    public ApiResponse<StatistiquesQuiz> statistiques(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(quizService.statistiques(id, user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuizResponse> create(@Valid @RequestBody QuizRequest request, CurrentUser user) {
        return ApiResponse.ok(quizService.create(request, user), "Quiz enregistré en brouillon");
    }

    @PutMapping("/{id}")
    public ApiResponse<QuizResponse> update(@PathVariable Long id, @Valid @RequestBody QuizRequest request,
                                            CurrentUser user) {
        return ApiResponse.ok(quizService.update(id, request, user), "Quiz modifié");
    }

    @PostMapping("/{id}/publier")
    public ApiResponse<QuizResponse> publier(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(quizService.publier(id, user), "Quiz publié");
    }

    @PostMapping("/{id}/depublier")
    public ApiResponse<QuizResponse> depublier(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(quizService.depublier(id, user), "Quiz repassé en brouillon");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, CurrentUser user) {
        quizService.delete(id, user);
        return ApiResponse.ok(null, "Quiz supprimé");
    }
}

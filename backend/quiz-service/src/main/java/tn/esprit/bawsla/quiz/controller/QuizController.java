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
import tn.esprit.bawsla.quiz.dto.NotionRateeDto;
import tn.esprit.bawsla.quiz.dto.QuizRequest;
import tn.esprit.bawsla.quiz.dto.QuizResponse;
import tn.esprit.bawsla.quiz.service.QuizService;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    public ApiResponse<List<QuizResponse>> findAll(@RequestParam(required = false) Long coursId, CurrentUser user) {
        return ApiResponse.ok(quizService.findAll(coursId));
    }

    /** Contrat inter-modules (module User / ML). STUB : renvoie une liste vide. */
    @GetMapping("/notions-ratees")
    public ApiResponse<List<NotionRateeDto>> notionsRatees(@RequestParam Long etudiantId, CurrentUser user) {
        return ApiResponse.ok(quizService.notionsRatees(etudiantId, user), "stub");
    }

    @GetMapping("/{id}")
    public ApiResponse<QuizResponse> findById(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(quizService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuizResponse> create(@Valid @RequestBody QuizRequest request, CurrentUser user) {
        return ApiResponse.ok(quizService.create(request, user), "Quiz créé");
    }

    @PutMapping("/{id}")
    public ApiResponse<QuizResponse> update(@PathVariable Long id, @Valid @RequestBody QuizRequest request,
                                            CurrentUser user) {
        return ApiResponse.ok(quizService.update(id, request, user), "Quiz modifié");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, CurrentUser user) {
        quizService.delete(id, user);
        return ApiResponse.ok(null, "Quiz supprimé");
    }
}

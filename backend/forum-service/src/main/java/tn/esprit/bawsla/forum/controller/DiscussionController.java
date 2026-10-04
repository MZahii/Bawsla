package tn.esprit.bawsla.forum.controller;

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
import tn.esprit.bawsla.forum.common.ApiResponse;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.DiscussionRequest;
import tn.esprit.bawsla.forum.dto.DiscussionResponse;
import tn.esprit.bawsla.forum.service.DiscussionService;

@RestController
@RequestMapping("/api/forum/discussions")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @GetMapping
    public ApiResponse<List<DiscussionResponse>> findAll(@RequestParam(required = false) Long coursId,
                                                         CurrentUser user) {
        return ApiResponse.ok(discussionService.findAll(coursId));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiscussionResponse> findById(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(discussionService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiscussionResponse> create(@Valid @RequestBody DiscussionRequest request, CurrentUser user) {
        return ApiResponse.ok(discussionService.create(request, user), "Discussion créée");
    }

    @PutMapping("/{id}")
    public ApiResponse<DiscussionResponse> update(@PathVariable Long id, @Valid @RequestBody DiscussionRequest request,
                                                  CurrentUser user) {
        return ApiResponse.ok(discussionService.update(id, request, user), "Discussion modifiée");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, CurrentUser user) {
        discussionService.delete(id, user);
        return ApiResponse.ok(null, "Discussion supprimée");
    }
}

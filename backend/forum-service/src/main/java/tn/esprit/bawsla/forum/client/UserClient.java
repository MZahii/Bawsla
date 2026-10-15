package tn.esprit.bawsla.forum.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import tn.esprit.bawsla.forum.common.ApiResponse;

/** user-service, contrat 4 : nom et rôle publics d'un membre (jamais transmis au LLM). */
@FeignClient(name = "user-service", contextId = "forumUserClient")
public interface UserClient {

    @GetMapping("/api/users/{id}")
    ApiResponse<UserDto> getUser(@PathVariable("id") Long id);
}

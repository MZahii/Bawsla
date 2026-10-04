package tn.esprit.bawsla.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.bawsla.user.common.ApiResponse;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.dto.UserPublicDto;
import tn.esprit.bawsla.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(CurrentUser user) {
        return ApiResponse.ok(userService.me(user));
    }

    /** ADMIN uniquement. */
    @GetMapping
    public ApiResponse<List<UserDto>> findAll(CurrentUser user) {
        return ApiResponse.ok(userService.findAll(user));
    }

    /** Contrat inter-modules : infos publiques d'un utilisateur. */
    @GetMapping("/{id}")
    public ApiResponse<UserPublicDto> findById(@PathVariable Long id, CurrentUser user) {
        return ApiResponse.ok(userService.findPublicById(id));
    }
}

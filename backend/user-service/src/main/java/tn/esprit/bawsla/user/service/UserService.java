package tn.esprit.bawsla.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.user.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.dto.UserPublicDto;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto me(CurrentUser current) {
        return UserDto.from(getOrThrow(current.id()));
    }

    public UserPublicDto findPublicById(Long id) {
        return UserPublicDto.from(getOrThrow(id));
    }

    public List<UserDto> findAll(CurrentUser current) {
        current.requireAnyRole(CurrentUser.ADMIN);
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }

    private User getOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }
}

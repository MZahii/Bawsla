package tn.esprit.bawsla.user.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ConflictException;
import tn.esprit.bawsla.user.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.AdminCreationRequest;
import tn.esprit.bawsla.user.dto.AdminModificationRequest;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.dto.UserPublicDto;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserDto me(CurrentUser current) {
        return UserDto.from(getOrThrow(current.id()));
    }

    public UserPublicDto findPublicById(Long id) {
        return UserPublicDto.from(getOrThrow(id));
    }

    /** ADMIN : tous les comptes, les plus récents d'abord. */
    public List<UserDto> findAll(CurrentUser current) {
        current.requireAnyRole(CurrentUser.ADMIN);
        return userRepository.findAllByOrderByDateCreationDesc().stream().map(UserDto::from).toList();
    }

    @Transactional
    public UserDto creer(CurrentUser current, AdminCreationRequest request) {
        current.requireAnyRole(CurrentUser.ADMIN);
        String email = normaliser(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Un compte existe déjà avec cet email");
        }
        User user = new User();
        user.setNom(request.nom().trim());
        user.setPrenom(request.prenom().trim());
        user.setEmail(email);
        user.setMotDePasse(passwordEncoder.encode(request.motDePasse()));
        user.setRole(request.role());
        user.setActif(request.actif() == null || request.actif());
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto modifier(CurrentUser current, Long id, AdminModificationRequest request) {
        current.requireAnyRole(CurrentUser.ADMIN);
        User user = getOrThrow(id);
        String email = normaliser(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("Un compte existe déjà avec cet email");
        }
        verifierAccesAdminConserve(current, user, request.role(), request.actif());
        user.setNom(request.nom().trim());
        user.setPrenom(request.prenom().trim());
        user.setEmail(email);
        user.setRole(request.role());
        user.setActif(request.actif());
        return UserDto.from(user);
    }

    @Transactional
    public UserDto changerStatut(CurrentUser current, Long id, boolean actif) {
        current.requireAnyRole(CurrentUser.ADMIN);
        User user = getOrThrow(id);
        verifierAccesAdminConserve(current, user, user.getRole(), actif);
        user.setActif(actif);
        return UserDto.from(user);
    }

    /** Définit un nouveau mot de passe et lève un éventuel verrouillage (trop d'échecs de connexion). */
    @Transactional
    public void reinitialiserMotDePasse(CurrentUser current, Long id, String motDePasse) {
        current.requireAnyRole(CurrentUser.ADMIN);
        User user = getOrThrow(id);
        user.setMotDePasse(passwordEncoder.encode(motDePasse));
        user.deverrouiller();
    }

    /**
     * Empêche la plateforme de se retrouver sans administrateur : l'admin ne peut ni se désactiver ni se
     * retirer le rôle ADMIN, et le dernier admin actif ne peut pas perdre ce statut.
     */
    private void verifierAccesAdminConserve(CurrentUser current, User cible, Role nouveauRole, boolean nouvelActif) {
        boolean perdAccesAdmin = cible.getRole() == Role.ADMIN && cible.isActif()
                && (nouveauRole != Role.ADMIN || !nouvelActif);
        if (!perdAccesAdmin) {
            return;
        }
        if (cible.getId().equals(current.id())) {
            throw new BadRequestException("Tu ne peux pas retirer ton propre accès administrateur");
        }
        if (userRepository.countByRoleAndActifTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("Impossible : c'est le dernier administrateur actif");
        }
    }

    private static String normaliser(String email) {
        return email.trim().toLowerCase();
    }

    private User getOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }
}

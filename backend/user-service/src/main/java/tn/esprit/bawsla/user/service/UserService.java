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
import tn.esprit.bawsla.user.dto.ChangementMotDePasseRequest;
import tn.esprit.bawsla.user.dto.ParcoursDto;
import tn.esprit.bawsla.user.dto.ProfilRequest;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.dto.UserPublicDto;
import tn.esprit.bawsla.user.entity.Parcours;
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

    @Transactional
    public UserDto modifierMonProfil(CurrentUser current, ProfilRequest request) {
        User user = getOrThrow(current.id());
        user.setNom(request.nom().trim());
        user.setPrenom(request.prenom().trim());
        String bio = request.bio() == null ? "" : request.bio().trim();
        user.setBio(bio.isEmpty() ? null : bio);
        return UserDto.from(user);
    }

    /**
     * Mauvais mot de passe actuel : 400 et non 401, car le front déconnecte l'utilisateur sur tout 401
     * hors /auth (session expirée), ce qui n'a pas lieu d'être ici.
     */
    @Transactional
    public void changerMonMotDePasse(CurrentUser current, ChangementMotDePasseRequest request) {
        User user = getOrThrow(current.id());
        if (!passwordEncoder.matches(request.ancienMotDePasse(), user.getMotDePasse())) {
            throw new BadRequestException("Mot de passe actuel incorrect");
        }
        if (passwordEncoder.matches(request.nouveauMotDePasse(), user.getMotDePasse())) {
            throw new BadRequestException("Le nouveau mot de passe doit être différent de l'actuel");
        }
        user.setMotDePasse(passwordEncoder.encode(request.nouveauMotDePasse()));
    }

    /**
     * « Supprimer mon compte » : le compte est désactivé, pas effacé, pour ne pas casser les cours, quiz et
     * messages des autres modules qui gardent son id. Un admin peut le réactiver.
     */
    @Transactional
    public void desactiverMonCompte(CurrentUser current, String motDePasse) {
        User user = getOrThrow(current.id());
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Un compte administrateur ne peut pas être supprimé depuis cette page");
        }
        if (!passwordEncoder.matches(motDePasse, user.getMotDePasse())) {
            throw new BadRequestException("Mot de passe incorrect");
        }
        user.setActif(false);
    }

    public ParcoursDto monParcours(CurrentUser current) {
        return ParcoursDto.from(getOrThrow(current.id()).getParcours());
    }

    @Transactional
    public ParcoursDto enregistrerMonParcours(CurrentUser current, ParcoursDto request) {
        User user = getOrThrow(current.id());
        Parcours p = user.getParcours() == null ? new Parcours() : user.getParcours();
        p.setProfil(DemandeFormateurService.texteOuNull(request.profil()));
        p.setObjectif(DemandeFormateurService.texteOuNull(request.objectif()));
        p.setDomaines(request.domaines() == null ? List.of()
                : request.domaines().stream().filter(d -> d != null && !d.isBlank()).map(String::trim).distinct().toList());
        p.setTemps(DemandeFormateurService.texteOuNull(request.temps()));
        user.setParcours(p);
        return ParcoursDto.from(p);
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
        // Un formateur créé par l'admin est validé d'office.
        user.setFormateurValide(request.role() == Role.ENSEIGNANT);
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
        if (request.role() != Role.ENSEIGNANT) {
            user.setFormateurValide(false);
        } else if (user.getRole() != Role.ENSEIGNANT) {
            // L'admin qui fait d'un compte un formateur le valide du même coup.
            user.setFormateurValide(true);
        }
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

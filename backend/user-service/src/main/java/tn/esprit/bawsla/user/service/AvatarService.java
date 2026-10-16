package tn.esprit.bawsla.user.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.UserDto;
import tn.esprit.bawsla.user.entity.Avatar;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.AvatarRepository;
import tn.esprit.bawsla.user.repository.UserRepository;

/**
 * Photo de profil : PNG, JPEG ou WebP, 2 Mo au plus. Le type est déduit des premiers octets du fichier
 * (signature), jamais du nom ou du Content-Type envoyé par le client.
 */
@Service
@Transactional(readOnly = true)
public class AvatarService {

    static final long TAILLE_MAX = 2L * 1024 * 1024;

    private final AvatarRepository avatarRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AvatarService(AvatarRepository avatarRepository, UserRepository userRepository, Clock clock) {
        this.avatarRepository = avatarRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public UserDto enregistrer(CurrentUser current, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Choisis une image");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new BadRequestException("Image trop lourde : 2 Mo maximum");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Image illisible");
        }
        String type = typeImage(contenu);
        if (type == null) {
            throw new BadRequestException("Format accepté : PNG, JPEG ou WebP");
        }
        User user = getUser(current.id());
        LocalDateTime maintenant = LocalDateTime.now(clock);
        Avatar avatar = avatarRepository.findById(user.getId()).orElseGet(Avatar::new);
        avatar.setUserId(user.getId());
        avatar.setContenu(contenu);
        avatar.setTypeMime(type);
        avatar.setDateMaj(maintenant);
        avatarRepository.save(avatar);
        user.setAvatarVersion(clock.millis());
        return UserDto.from(user);
    }

    @Transactional
    public UserDto supprimer(CurrentUser current) {
        User user = getUser(current.id());
        avatarRepository.deleteById(user.getId());
        user.setAvatarVersion(null);
        return UserDto.from(user);
    }

    /** Photo d'un utilisateur, pour tout utilisateur authentifié (comme son nom, cf. GET /api/users/{id}). */
    public Avatar lire(Long userId) {
        return avatarRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo de l'utilisateur", userId));
    }

    /** Type MIME d'après la signature du fichier, null si ce n'est pas une image acceptée. */
    static String typeImage(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }
}

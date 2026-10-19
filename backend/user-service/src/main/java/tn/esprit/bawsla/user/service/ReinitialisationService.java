package tn.esprit.bawsla.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.dto.MotDePasseOublieRequest;
import tn.esprit.bawsla.user.dto.ReinitialisationRequest;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

/**
 * « Mot de passe oublié » : un lien à usage unique, valable 30 minutes, envoyé par email.
 * - La réponse est la même que le compte existe ou non (pas d'énumération des emails).
 * - Seule l'empreinte SHA-256 du jeton est stockée : une fuite de la base ne donne aucun lien utilisable.
 * - Un nouveau lien n'est pas renvoyé moins d'une minute après le précédent (anti-spam de la boîte mail).
 */
@Service
public class ReinitialisationService {

    static final Duration VALIDITE = Duration.ofMinutes(30);
    static final Duration DELAI_RENVOI = Duration.ofMinutes(1);
    static final String LIEN_INVALIDE = "Ce lien n'est plus valide. Demande un nouveau lien de réinitialisation.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final Clock clock;
    private final String frontUrl;

    public ReinitialisationService(UserRepository userRepository, PasswordEncoder passwordEncoder, MailService mailService,
                                   Clock clock, @Value("${bawsla.front-url:http://localhost:4200}") String frontUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.clock = clock;
        this.frontUrl = frontUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void demander(MotDePasseOublieRequest request) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(User::isActif)
                .filter(u -> !demandeTropRecente(u, maintenant))
                .ifPresent(u -> {
                    String token = nouveauJeton();
                    u.preparerReinitialisation(empreinte(token), maintenant.plus(VALIDITE));
                    mailService.envoyerLienReinitialisation(u.getEmail(), u.getPrenom(),
                            frontUrl + "/reinitialiser?token=" + token, VALIDITE.toMinutes());
                });
    }

    @Transactional
    public void reinitialiser(ReinitialisationRequest request) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        User user = userRepository.findByReinitialisationHash(empreinte(request.token().trim()))
                .filter(u -> u.reinitialisationValide(maintenant) && u.isActif())
                .orElseThrow(() -> new BadRequestException(LIEN_INVALIDE));
        user.reinitialiserMotDePasse(passwordEncoder.encode(request.motDePasse()));
    }

    private static boolean demandeTropRecente(User u, LocalDateTime maintenant) {
        return u.reinitialisationValide(maintenant)
                && u.getReinitialisationExpire().minus(VALIDITE).plus(DELAI_RENVOI).isAfter(maintenant);
    }

    /** 32 octets aléatoires, en Base64 URL (utilisable tel quel dans un lien). */
    private static String nouveauJeton() {
        byte[] octets = new byte[32];
        RANDOM.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    static String empreinte(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}

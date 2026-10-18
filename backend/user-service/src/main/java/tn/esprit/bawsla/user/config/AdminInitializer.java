package tn.esprit.bawsla.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.UserRepository;

/** Crée le compte ADMIN (ADMIN_EMAIL / ADMIN_PASSWORD) au démarrage s'il n'existe pas. */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    /** Valeur par défaut d'application.yml, connue de tous. */
    static final String MOT_DE_PASSE_PAR_DEFAUT = "Admin123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            @Value("${bawsla.admin.email}") String adminEmail,
                            @Value("${bawsla.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (MOT_DE_PASSE_PAR_DEFAUT.equals(adminPassword)) {
            log.warn("ADMIN_PASSWORD absent : mot de passe ADMIN par défaut (public). Le changer dans .env avant toute démo hors de ce poste.");
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            return;
        }
        User admin = new User();
        admin.setNom("Admin");
        admin.setPrenom("Bawsla");
        admin.setEmail(adminEmail.toLowerCase());
        admin.setMotDePasse(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("Compte ADMIN créé : {}", adminEmail);
    }
}

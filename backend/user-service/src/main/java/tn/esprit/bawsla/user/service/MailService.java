package tn.esprit.bawsla.user.service;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * Emails transactionnels du module User (pour l'instant : le lien « mot de passe oublié »).
 * SMTP configuré par MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD (.env). Sans MAIL_HOST, rien ne part :
 * le lien est écrit dans les logs, pour pouvoir tester en local sans serveur mail.
 * L'envoi se fait en arrière-plan : la réponse HTTP ne dépend ni de la lenteur du SMTP ni de l'existence du compte.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSenderImpl sender;
    private final String expediteur;

    public MailService(@Value("${bawsla.mail.host:}") String host,
                       @Value("${bawsla.mail.port:587}") int port,
                       @Value("${bawsla.mail.username:}") String username,
                       @Value("${bawsla.mail.password:}") String password,
                       @Value("${bawsla.mail.starttls:true}") boolean starttls,
                       @Value("${bawsla.mail.from:Bawsla <no-reply@bawsla.tn>}") String expediteur) {
        this.expediteur = expediteur;
        if (host.isBlank()) {
            this.sender = null;
            return;
        }
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(host);
        impl.setPort(port);
        impl.setDefaultEncoding("UTF-8");
        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        if (!username.isBlank()) {
            impl.setUsername(username);
            impl.setPassword(password);
            props.put("mail.smtp.auth", "true");
        }
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        this.sender = impl;
    }

    public void envoyerLienReinitialisation(String email, String prenom, String lien, long minutesValidite) {
        if (sender == null) {
            log.warn("MAIL_HOST non configuré : lien de réinitialisation (dev uniquement) : {}", lien);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(email);
        message.setSubject("Réinitialise ton mot de passe Bawsla");
        message.setText("""
                Bonjour %s,

                Tu as demandé à changer ton mot de passe Bawsla. Clique sur ce lien pour en choisir un nouveau :

                %s

                Ce lien est valable %d minutes et ne sert qu'une fois.
                Si tu n'es pas à l'origine de cette demande, ignore cet email : ton mot de passe reste inchangé.

                L'équipe Bawsla
                """.formatted(prenom, lien, minutesValidite));
        Thread.ofVirtual().start(() -> {
            try {
                sender.send(message);
            } catch (RuntimeException e) {
                log.error("Échec de l'envoi de l'email de réinitialisation : {}", e.getMessage());
            }
        });
    }
}

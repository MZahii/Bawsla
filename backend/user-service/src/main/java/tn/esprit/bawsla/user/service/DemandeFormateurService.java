package tn.esprit.bawsla.user.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.user.common.exception.BadRequestException;
import tn.esprit.bawsla.user.common.exception.ConflictException;
import tn.esprit.bawsla.user.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.user.common.security.CurrentUser;
import tn.esprit.bawsla.user.dto.DemandeFormateurDto;
import tn.esprit.bawsla.user.dto.DemandeFormateurRequest;
import tn.esprit.bawsla.user.entity.DemandeFormateur;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.StatutDemande;
import tn.esprit.bawsla.user.entity.User;
import tn.esprit.bawsla.user.repository.DemandeFormateurRepository;
import tn.esprit.bawsla.user.repository.UserRepository;

/**
 * Demandes pour devenir formateur. N'importe qui peut postuler (à l'inscription ou plus tard depuis « Mon compte ») ;
 * l'ADMIN accepte (le compte devient ENSEIGNANT validé) ou refuse avec un motif visible par le demandeur.
 */
@Service
@Transactional(readOnly = true)
public class DemandeFormateurService {

    private final DemandeFormateurRepository demandeRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public DemandeFormateurService(DemandeFormateurRepository demandeRepository, UserRepository userRepository,
                                   Clock clock) {
        this.demandeRepository = demandeRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Appelée par AuthService à l'inscription d'un formateur qui a renseigné son expertise. */
    @Transactional
    public void creerPourInscription(User user, String expertise, String presentation, String lien) {
        demandeRepository.save(nouvelle(user, expertise, presentation, lien));
    }

    /** Dernière demande de l'utilisateur courant, vide s'il n'en a jamais fait. */
    public Optional<DemandeFormateurDto> maDemande(CurrentUser current) {
        return demandeRepository.findFirstByUtilisateurIdOrderByDateCreationDesc(current.id())
                .map(DemandeFormateurDto::from);
    }

    @Transactional
    public DemandeFormateurDto soumettre(CurrentUser current, DemandeFormateurRequest request) {
        User user = userRepository.findById(current.id())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", current.id()));
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Un administrateur n'a pas besoin de demande formateur");
        }
        if (user.isFormateurValide()) {
            throw new ConflictException("Tu es déjà formateur validé");
        }
        if (demandeRepository.existsByUtilisateurIdAndStatut(user.getId(), StatutDemande.EN_ATTENTE)) {
            throw new ConflictException("Une demande est déjà en cours d'examen");
        }
        return DemandeFormateurDto.from(
                demandeRepository.save(nouvelle(user, request.expertise(), request.presentation(), request.lien())));
    }

    /** ADMIN : toutes les demandes, les plus récentes d'abord (le front filtre par statut). */
    public List<DemandeFormateurDto> lister(CurrentUser current) {
        current.requireAnyRole(CurrentUser.ADMIN);
        return demandeRepository.findAllByOrderByDateCreationDesc().stream().map(DemandeFormateurDto::from).toList();
    }

    /** ADMIN : le demandeur devient formateur validé (un apprenant change de rôle). */
    @Transactional
    public DemandeFormateurDto accepter(CurrentUser current, Long id) {
        current.requireAnyRole(CurrentUser.ADMIN);
        DemandeFormateur demande = enAttente(id);
        User user = demande.getUtilisateur();
        user.setRole(Role.ENSEIGNANT);
        user.setFormateurValide(true);
        demande.accepter(current.id(), LocalDateTime.now(clock));
        return DemandeFormateurDto.from(demande);
    }

    /** ADMIN : refus motivé ; le rôle du demandeur ne change pas et il pourra postuler de nouveau. */
    @Transactional
    public DemandeFormateurDto refuser(CurrentUser current, Long id, String motif) {
        current.requireAnyRole(CurrentUser.ADMIN);
        DemandeFormateur demande = enAttente(id);
        demande.refuser(current.id(), motif.trim(), LocalDateTime.now(clock));
        return DemandeFormateurDto.from(demande);
    }

    private DemandeFormateur enAttente(Long id) {
        DemandeFormateur demande = demandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande formateur", id));
        if (demande.getStatut() != StatutDemande.EN_ATTENTE) {
            throw new ConflictException("Cette demande a déjà été traitée");
        }
        return demande;
    }

    private DemandeFormateur nouvelle(User user, String expertise, String presentation, String lien) {
        DemandeFormateur d = new DemandeFormateur();
        d.setUtilisateur(user);
        d.setExpertise(expertise.trim());
        d.setPresentation(texteOuNull(presentation));
        d.setLien(texteOuNull(lien));
        d.setDateCreation(LocalDateTime.now(clock));
        return d;
    }

    static String texteOuNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

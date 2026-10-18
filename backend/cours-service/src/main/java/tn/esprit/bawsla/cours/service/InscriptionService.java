package tn.esprit.bawsla.cours.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ForbiddenException;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.InscriptionResponse;
import tn.esprit.bawsla.cours.dto.InscriptionResumeResponse;
import tn.esprit.bawsla.cours.dto.ProgressionResponse;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.Inscription;
import tn.esprit.bawsla.cours.repository.ChapitreRepository;
import tn.esprit.bawsla.cours.repository.ChapitreTermineRepository;
import tn.esprit.bawsla.cours.repository.CommandeRepository;
import tn.esprit.bawsla.cours.repository.InscriptionRepository;

/**
 * Inscriptions des apprenants et progression par chapitre.
 *
 * <p><b>Paiement (simulé) :</b> l'inscription directe n'est permise que pour un cours gratuit, ou payant déjà acheté. Sinon l'apprenant
 * passe par {@link CommandeService#payer}, qui enregistre la commande puis l'inscrit. Le contenu d'un cours payant est réservé aux
 * inscrits ({@link CoursService#accesContenu}).</p>
 *
 * <p>Aucune transaction n'est ouverte par cette classe (voir {@link InscriptionEnregistrement}) : chaque lecture voit l'état validé le plus
 * récent, y compris après une inscription concurrente.</p>
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class InscriptionService {

    /** Résultat de {@link #inscrire} : {@code creee} vaut false si l'apprenant était déjà inscrit (réponse idempotente, 200). */
    public record Inscrite(InscriptionResponse inscription, boolean creee) {
    }

    private final CoursService coursService;
    private final InscriptionRepository inscriptionRepository;
    private final ChapitreTermineRepository termineRepository;
    private final ChapitreRepository chapitreRepository;
    private final InscriptionEnregistrement enregistrement;
    private final CommandeRepository commandeRepository;

    public InscriptionService(CoursService coursService, InscriptionRepository inscriptionRepository,
                              ChapitreTermineRepository termineRepository, ChapitreRepository chapitreRepository,
                              InscriptionEnregistrement enregistrement, CommandeRepository commandeRepository) {
        this.coursService = coursService;
        this.inscriptionRepository = inscriptionRepository;
        this.termineRepository = termineRepository;
        this.chapitreRepository = chapitreRepository;
        this.enregistrement = enregistrement;
        this.commandeRepository = commandeRepository;
    }

    // ------------------------------------------------------------------ apprenant

    /** Seul un apprenant s'inscrit, et seulement à un cours publié (un brouillon ou un cours archivé n'existe pas pour lui : 404). */
    public Inscrite inscrire(Long coursId, CurrentUser user) {
        exigerApprenant(user, "Seul un apprenant peut s'inscrire à un cours.");
        Cours cours = coursService.chargerVisible(coursId, user);
        Optional<Inscription> existante = inscriptionRepository.findByEtudiantIdAndCoursId(user.id(), coursId);
        if (existante.isPresent()) {
            return new Inscrite(reponse(existante.get()), false);
        }
        if (cours.getPrix() != null && cours.getPrix().signum() > 0 && !commandeRepository.existsByEtudiantIdAndCoursId(user.id(), coursId)) {
            throw new ForbiddenException("Ce cours est payant : passe par le paiement pour t'inscrire.");
        }
        try {
            return new Inscrite(reponse(enregistrement.creer(user.id(), coursId)), true);
        } catch (DataIntegrityViolationException e) {
            // Double clic : une autre requête vient de créer l'inscription. On renvoie celle-là, sans doublon.
            return new Inscrite(reponse(inscriptionRepository.findByEtudiantIdAndCoursId(user.id(), coursId).orElseThrow()), false);
        }
    }

    /** Supprime l'inscription ET ses chapitres terminés (définitif). Sans inscription, il n'y a rien à faire : la réponse reste un succès. */
    public void desinscrire(Long coursId, CurrentUser user) {
        exigerApprenant(user, "Seul un apprenant peut se désinscrire d'un cours.");
        inscriptionRepository.findByEtudiantIdAndCoursId(user.id(), coursId).ifPresent(i -> enregistrement.supprimer(i.getId()));
    }

    public ProgressionResponse terminerChapitre(Long coursId, Long chapitreId, CurrentUser user) {
        Inscription inscription = inscriptionPourChapitre(coursId, chapitreId, user);
        enregistrement.marquer(inscription.getId(), chapitreId, nombreDeChapitres(coursId));
        return progression(inscriptionRepository.findById(inscription.getId()).orElseThrow());
    }

    public ProgressionResponse annulerChapitre(Long coursId, Long chapitreId, CurrentUser user) {
        Inscription inscription = inscriptionPourChapitre(coursId, chapitreId, user);
        enregistrement.annuler(inscription.getId(), chapitreId, nombreDeChapitres(coursId));
        return progression(inscriptionRepository.findById(inscription.getId()).orElseThrow());
    }

    /** Progression de l'apprenant dans ce cours, avec les chapitres terminés ; {@code null} s'il n'y est pas inscrit (ce n'est pas une erreur). */
    public ProgressionResponse maProgression(Long coursId, CurrentUser user) {
        exigerApprenant(user, "Seul un apprenant a une progression dans un cours.");
        coursService.chargerVisible(coursId, user);
        return inscriptionRepository.findByEtudiantIdAndCoursId(user.id(), coursId).map(this::progression).orElse(null);
    }

    public List<InscriptionResponse> mesInscriptions(CurrentUser user) {
        return reponses(inscriptionRepository.findByEtudiantIdOrderByDateInscriptionDescIdDesc(user.id()));
    }

    /**
     * Lot 2 : POST /api/cours/{id}/activite, envoyé par le lecteur toutes les 60 s quand l'onglet est visible. Apprenant inscrit à un
     * cours publié seulement (403 sinon, 404 si le cours n'est pas visible). Renvoie l'inscription à jour (temps passé).
     */
    public InscriptionResponse signalerActivite(Long coursId, CurrentUser user) {
        exigerApprenant(user, "Seul un apprenant a un temps passé dans un cours.");
        coursService.chargerVisible(coursId, user);
        Inscription inscription = inscriptionRepository.findByEtudiantIdAndCoursId(user.id(), coursId)
                .orElseThrow(() -> new ForbiddenException("Inscris-toi à ce cours pour suivre ton temps passé."));
        enregistrement.signalerActivite(inscription.getId());
        return reponse(inscriptionRepository.findById(inscription.getId()).orElseThrow());
    }

    // ------------------------------------------------------------------ contrat 8

    /** GET /api/cours/inscriptions/toutes : ADMIN uniquement. Toutes les inscriptions, sans autre donnée sur l'apprenant que son identifiant. */
    public List<InscriptionResumeResponse> toutes(CurrentUser user) {
        user.requireAnyRole(CurrentUser.ADMIN);
        return reponses(inscriptionRepository.findAllByOrderByIdAsc()).stream().map(InscriptionResumeResponse::from).toList();
    }

    // ------------------------------------------------------------------ contrat 7

    /**
     * GET /api/cours/inscriptions?etudiantId= : l'apprenant lui-même, tout ENSEIGNANT (demande du module User : voir le journal, un
     * formateur peut ainsi lire les inscriptions d'un apprenant à des cours qui ne sont pas les siens) ou un ADMIN.
     */
    public List<InscriptionResponse> parEtudiant(Long etudiantId, CurrentUser user) {
        if (etudiantId == null) {
            throw new BadRequestException("Le paramètre etudiantId est obligatoire.");
        }
        if (user.hasAnyRole(CurrentUser.ETUDIANT) && !etudiantId.equals(user.id())) {
            throw new ForbiddenException("Tu ne peux consulter que tes propres inscriptions.");
        }
        return reponses(inscriptionRepository.findByEtudiantIdOrderByDateInscriptionDescIdDesc(etudiantId));
    }

    /** GET /api/cours/{id}/inscriptions : le formateur propriétaire du cours ou un ADMIN. 404 si le cours n'existe pas. */
    public List<InscriptionResponse> parCours(Long coursId, CurrentUser user) {
        user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        coursService.chargerPourEcriture(coursId, user);
        return reponses(inscriptionRepository.findByCoursIdOrderByDateInscriptionAscIdAsc(coursId));
    }

    // ------------------------------------------------------------------ interne

    private static void exigerApprenant(CurrentUser user, String message) {
        if (!user.hasAnyRole(CurrentUser.ETUDIANT)) {
            throw new ForbiddenException(message);
        }
    }

    /** Apprenant inscrit, cours visible, chapitre appartenant à ce cours : sinon 403 (rôle, pas inscrit) ou 404 (cours, chapitre). */
    private Inscription inscriptionPourChapitre(Long coursId, Long chapitreId, CurrentUser user) {
        exigerApprenant(user, "Seul un apprenant peut suivre sa progression dans un cours.");
        Cours cours = coursService.chargerVisible(coursId, user);
        if (!chapitreRepository.existsByIdAndCoursId(chapitreId, cours.getId())) {
            throw new ResourceNotFoundException("Chapitre", chapitreId);
        }
        return inscriptionRepository.findByEtudiantIdAndCoursId(user.id(), coursId)
                .orElseThrow(() -> new ForbiddenException("Inscris-toi à ce cours pour suivre ta progression."));
    }

    private int nombreDeChapitres(Long coursId) {
        return chapitreRepository.statistiques(List.of(coursId)).stream().mapToInt(l -> ((Number) l[1]).intValue()).sum();
    }

    private InscriptionResponse reponse(Inscription inscription) {
        return reponses(List.of(inscription)).get(0);
    }

    private ProgressionResponse progression(Inscription inscription) {
        return ProgressionResponse.from(reponse(inscription), termineRepository.chapitresTermines(inscription.getId()));
    }

    /** Chapitres comptés par deux requêtes groupées pour toute la liste (pas de requête par inscription). */
    private List<InscriptionResponse> reponses(Collection<Inscription> inscriptions) {
        if (inscriptions.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> termines = new HashMap<>();
        for (Object[] l : termineRepository.compterParInscription(inscriptions.stream().map(Inscription::getId).toList())) {
            termines.put((Long) l[0], ((Number) l[1]).intValue());
        }
        Map<Long, Integer> totaux = new HashMap<>();
        for (Object[] l : chapitreRepository.statistiques(inscriptions.stream().map(Inscription::getCoursId).distinct().toList())) {
            totaux.put((Long) l[0], ((Number) l[1]).intValue());
        }
        return inscriptions.stream()
                .map(i -> InscriptionResponse.from(i, termines.getOrDefault(i.getId(), 0), totaux.getOrDefault(i.getCoursId(), 0)))
                .toList();
    }
}

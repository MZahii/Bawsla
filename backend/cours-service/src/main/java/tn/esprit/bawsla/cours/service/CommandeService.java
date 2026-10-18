package tn.esprit.bawsla.cours.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ForbiddenException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CommandeResponse;
import tn.esprit.bawsla.cours.dto.PaiementRequest;
import tn.esprit.bawsla.cours.entity.Commande;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.repository.CommandeRepository;
import tn.esprit.bawsla.cours.repository.CoursRepository;

/**
 * Achat d'un cours payant par un <b>paiement simulé</b> : aucune banque n'est contactée et aucune somme n'est débitée. La carte est
 * contrôlée comme le ferait un formulaire de paiement (Luhn, date d'expiration, cryptogramme) ; la carte de test
 * {@value #CARTE_REFUSEE} est toujours refusée, pour montrer le cas d'échec. On ne garde que les 4 derniers chiffres.
 *
 * <p>Un paiement réussi enregistre la commande puis inscrit l'apprenant ({@link InscriptionService#inscrire}). Rejouer le paiement
 * d'un cours déjà acheté ne crée pas de seconde commande : on renvoie la première (et on réinscrit l'apprenant s'il s'était désinscrit).</p>
 */
@Service
public class CommandeService {

    static final String CARTE_REFUSEE = "4000000000000002";
    private static final DateTimeFormatter JOUR = DateTimeFormatter.BASIC_ISO_DATE;
    private static final SecureRandom ALEA = new SecureRandom();

    /** Résultat de {@link #payer} : {@code creee} vaut false si le cours était déjà acheté. */
    public record Payee(CommandeResponse commande, boolean creee) {
    }

    private final CoursService coursService;
    private final InscriptionService inscriptionService;
    private final CommandeRepository commandeRepository;
    private final CoursRepository coursRepository;

    public CommandeService(CoursService coursService, InscriptionService inscriptionService, CommandeRepository commandeRepository,
                           CoursRepository coursRepository) {
        this.coursService = coursService;
        this.inscriptionService = inscriptionService;
        this.commandeRepository = commandeRepository;
        this.coursRepository = coursRepository;
    }

    /** POST /api/cours/{id}/commandes : apprenant, cours publié et payant. */
    public Payee payer(Long coursId, PaiementRequest request, CurrentUser user) {
        if (user == null || !user.hasAnyRole(CurrentUser.ETUDIANT)) {
            throw new ForbiddenException("Seul un apprenant peut acheter un cours.");
        }
        Cours cours = coursService.chargerVisible(coursId, user);
        if (cours.getPrix() == null || cours.getPrix().signum() <= 0) {
            throw new BadRequestException("Ce cours est gratuit : inscris-toi directement.");
        }
        var existante = commandeRepository.findByEtudiantIdAndCoursId(user.id(), coursId);
        if (existante.isPresent()) {
            inscriptionService.inscrire(coursId, user);
            return new Payee(CommandeResponse.from(existante.get(), cours.getTitre()), false);
        }

        String numero = request.numeroCarte().replace(" ", "");
        verifierCarte(numero, request.expiration());

        Commande commande;
        boolean creee = true;
        try {
            commande = commandeRepository.saveAndFlush(new Commande(reference(), user.id(), coursId, cours.getPrix(),
                    numero.substring(numero.length() - 4), marque(numero)));
        } catch (DataIntegrityViolationException e) {
            // Double clic : l'autre requête a déjà enregistré la commande.
            commande = commandeRepository.findByEtudiantIdAndCoursId(user.id(), coursId).orElseThrow(() -> e);
            creee = false;
        }
        inscriptionService.inscrire(coursId, user);
        return new Payee(CommandeResponse.from(commande, cours.getTitre()), creee);
    }

    /** GET /api/cours/commandes/mes : achats de l'apprenant connecté, du plus récent au plus ancien. */
    public List<CommandeResponse> mesCommandes(CurrentUser user) {
        if (user == null) {
            throw new ForbiddenException("Connecte-toi pour voir tes achats.");
        }
        return reponses(commandeRepository.findByEtudiantIdOrderByDatePaiementDescIdDesc(user.id()));
    }

    /** GET /api/cours/commandes/ventes : toutes les ventes (ADMIN) ou celles des cours du formateur connecté (ENSEIGNANT). */
    public List<CommandeResponse> ventes(CurrentUser user) {
        if (user == null) {
            throw new ForbiddenException("Accès réservé aux formateurs et aux administrateurs.");
        }
        user.requireAnyRole(CurrentUser.ADMIN, CurrentUser.ENSEIGNANT);
        if (user.isAdmin()) {
            return reponses(commandeRepository.findAllByOrderByDatePaiementDescIdDesc());
        }
        List<Long> mesCours = coursRepository.findByEnseignantId(user.id()).stream().map(Cours::getId).toList();
        return mesCours.isEmpty() ? List.of() : reponses(commandeRepository.findByCoursIdInOrderByDatePaiementDescIdDesc(mesCours));
    }

    // ------------------------------------------------------------------ interne

    private List<CommandeResponse> reponses(List<Commande> commandes) {
        Collection<Long> ids = commandes.stream().map(Commande::getCoursId).distinct().toList();
        Map<Long, String> titres = coursRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Cours::getId, Cours::getTitre, (a, b) -> a));
        return commandes.stream().map(c -> CommandeResponse.from(c, titres.get(c.getCoursId()))).toList();
    }

    static void verifierCarte(String numero, String expiration) {
        if (numero.length() < 12 || numero.length() > 19 || !luhn(numero)) {
            throw new BadRequestException("Numéro de carte invalide.");
        }
        YearMonth fin = YearMonth.of(2000 + Integer.parseInt(expiration.substring(3)), Integer.parseInt(expiration.substring(0, 2)));
        if (fin.isBefore(YearMonth.now())) {
            throw new BadRequestException("Cette carte a expiré.");
        }
        if (CARTE_REFUSEE.equals(numero)) {
            throw new BadRequestException("Paiement refusé par la banque (carte de test refusée). Aucun montant n'a été débité.");
        }
    }

    static boolean luhn(String numero) {
        int somme = 0;
        boolean doubler = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int chiffre = numero.charAt(i) - '0';
            if (doubler) {
                chiffre *= 2;
                if (chiffre > 9) {
                    chiffre -= 9;
                }
            }
            somme += chiffre;
            doubler = !doubler;
        }
        return somme % 10 == 0;
    }

    static String marque(String numero) {
        if (numero.startsWith("4")) {
            return "Visa";
        }
        int deux = Integer.parseInt(numero.substring(0, 2));
        int quatre = Integer.parseInt(numero.substring(0, 4));
        if ((deux >= 51 && deux <= 55) || (quatre >= 2221 && quatre <= 2720)) {
            return "Mastercard";
        }
        return "Carte";
    }

    /** Référence lisible et unique : BW-AAAAMMJJ-XXXXXX. */
    private static String reference() {
        byte[] octets = new byte[3];
        ALEA.nextBytes(octets);
        return "BW-" + LocalDate.now().format(JOUR) + "-" + HexFormat.of().withUpperCase().formatHex(octets);
    }
}

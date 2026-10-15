package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ConflictException;
import tn.esprit.bawsla.forum.common.exception.ForbiddenException;
import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.DiscussionDetail;
import tn.esprit.bawsla.forum.dto.DiscussionModificationRequest;
import tn.esprit.bawsla.forum.dto.DiscussionRequest;
import tn.esprit.bawsla.forum.dto.DiscussionResume;
import tn.esprit.bawsla.forum.dto.PageResponse;
import tn.esprit.bawsla.forum.dto.VersionResponse;
import tn.esprit.bawsla.forum.entity.Abonnement;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Etiquette;
import tn.esprit.bawsla.forum.entity.Favori;
import tn.esprit.bawsla.forum.entity.Lecture;
import tn.esprit.bawsla.forum.entity.Reponse;
import tn.esprit.bawsla.forum.entity.VersionDiscussion;
import tn.esprit.bawsla.forum.entity.Vue;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;
import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;
import tn.esprit.bawsla.forum.evenement.DiscussionCreee;
import tn.esprit.bawsla.forum.evenement.DiscussionModifiee;
import tn.esprit.bawsla.forum.evenement.ReponseAcceptee;
import tn.esprit.bawsla.forum.repository.DiscussionRepository;
import tn.esprit.bawsla.forum.repository.LectureRepository;
import tn.esprit.bawsla.forum.repository.ReponseRepository;
import tn.esprit.bawsla.forum.repository.VersionDiscussionRepository;
import tn.esprit.bawsla.forum.repository.VueRepository;
import tn.esprit.bawsla.forum.service.AccesService.Contexte;
import tn.esprit.bawsla.forum.service.FilService.Fil;

/** Discussions : liste filtrée, fil complet, publication, édition (avec versions), suppression, meilleure réponse. */
@Service
@Transactional(readOnly = true)
public class DiscussionService {

    public static final int TAILLE_PAR_DEFAUT = 20;
    public static final int TAILLE_MAX = 50;

    /** Filtres de la liste (paramètres de GET /api/forum/discussions). */
    public record Filtres(Long coursId, Long chapitreId, TypeDiscussion type, String statut, String etiquette,
                          String filtre, String tri, String q, String mode, Integer page, Integer taille) {

        Filtres sansRecherche() {
            return new Filtres(coursId, chapitreId, type, statut, etiquette, filtre, tri, null, mode, page, taille);
        }
    }

    private final DiscussionRepository discussionRepository;
    private final ReponseRepository reponseRepository;
    private final VersionDiscussionRepository versionRepository;
    private final VueRepository vueRepository;
    private final LectureRepository lectureRepository;
    private final AccesService acces;
    private final FilService fils;
    private final EtiquetteService etiquettes;
    private final PresentationService presentation;
    private final JournalService journal;
    private final RechercheService recherche;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public DiscussionService(DiscussionRepository discussionRepository, ReponseRepository reponseRepository,
                             VersionDiscussionRepository versionRepository, VueRepository vueRepository,
                             LectureRepository lectureRepository, AccesService acces, FilService fils,
                             EtiquetteService etiquettes, PresentationService presentation, JournalService journal,
                             RechercheService recherche, ApplicationEventPublisher evenements, Clock horloge) {
        this.discussionRepository = discussionRepository;
        this.reponseRepository = reponseRepository;
        this.versionRepository = versionRepository;
        this.vueRepository = vueRepository;
        this.lectureRepository = lectureRepository;
        this.acces = acces;
        this.fils = fils;
        this.etiquettes = etiquettes;
        this.presentation = presentation;
        this.journal = journal;
        this.recherche = recherche;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ Lecture

    public PageResponse<DiscussionResume> lister(Filtres f, CurrentUser user) {
        acces.exigerConnecte(user);
        if (f.coursId() != null) {
            acces.contexte(user, f.coursId());
        }
        int page = f.page() == null ? 0 : Math.max(0, f.page());
        int taille = f.taille() == null ? TAILLE_PAR_DEFAUT : Math.clamp(f.taille(), 1, TAILLE_MAX);
        if (f.q() != null && !f.q().isBlank()) {
            return listerRecherche(f, user, page, taille);
        }
        Page<Discussion> resultat = discussionRepository.findAll(specification(f, user),
                PageRequest.of(page, taille, tri(f.tri())));
        return PageResponse.of(presentation.resumes(resultat.getContent(), user), page, taille,
                resultat.getTotalElements());
    }

    /**
     * Recherche (barre « Cherche ou pose ta question… ») : hybride RRF par défaut, mots exacts en mode lexical.
     * Les autres filtres s'appliquent aux résultats ; tri par pertinence, sauf si un autre tri est demandé.
     */
    private PageResponse<DiscussionResume> listerRecherche(Filtres f, CurrentUser user, int page, int taille) {
        RechercheService.Resultat r = recherche.rechercher(f.coursId(), f.q().trim(), f.mode());
        List<Long> ids = r.ids();
        if (ids.isEmpty()) {
            return PageResponse.of(List.of(), page, taille, 0);
        }
        Specification<Discussion> spec = specification(f.sansRecherche(), user)
                .and((racine, requete, cb) -> racine.get("id").in(ids));
        List<Discussion> trouvees = f.tri() == null || "pertinence".equals(f.tri())
                ? discussionRepository.findAll(spec).stream()
                        .sorted(Comparator.comparingInt(d -> ids.indexOf(d.getId()))).toList()
                : discussionRepository.findAll(spec, tri(f.tri()));
        int debut = Math.min(page * taille, trouvees.size());
        List<Discussion> morceau = trouvees.subList(debut, Math.min(debut + taille, trouvees.size()));
        return PageResponse.of(presentation.resumes(morceau, user, r.scores()), page, taille, trouvees.size());
    }

    /** Ouvre un fil : compte la vue (une par utilisateur et par jour) et le marque comme lu. */
    @Transactional
    public DiscussionDetail ouvrir(Long id, CurrentUser user) {
        Fil fil = fils.charger(id, user);
        Discussion d = fil.discussion();
        LocalDate jour = LocalDate.now(horloge);
        if (!vueRepository.existsByUserIdAndDiscussionIdAndJour(user.id(), id, jour)) {
            Vue vue = new Vue();
            vue.setUserId(user.id());
            vue.setDiscussionId(id);
            vue.setJour(jour);
            vueRepository.save(vue);
            d.setNbVues(d.getNbVues() + 1);
        }
        marquerLu(user.id(), id);
        return presentation.detail(d, fil.ctx());
    }

    public List<VersionResponse> versions(Long id, CurrentUser user) {
        fils.charger(id, user);
        return versionRepository.findByDiscussionIdOrderByModifieLeDesc(id).stream().map(VersionResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------ Écriture

    @Transactional
    public DiscussionDetail publier(DiscussionRequest req, CurrentUser user) {
        Contexte ctx = acces.contexte(user, req.coursId());
        acces.exigerEcriture(ctx);
        if (req.type() == TypeDiscussion.ANNONCE && !ctx.moderateur()) {
            throw new ForbiddenException("Seuls le formateur du cours et l'administration publient des annonces");
        }
        if (req.anonyme()) {
            acces.exigerAnonymatPermis(ctx);
        }
        acces.exigerSousLimites(ctx, req.type() == TypeDiscussion.QUESTION);
        if (req.type() == TypeDiscussion.QUESTION && !req.confirmer()) {
            Long doublon = recherche.similaires(req.coursId(), req.titre().trim(), null).doublonId();
            if (doublon != null) {
                throw new ConflictException("Doublon probable : une question très proche est déjà résolue "
                        + "(discussion n° " + doublon + "). Lis sa réponse, ou confirme la publication.");
            }
        }

        Discussion d = new Discussion();
        d.setCoursId(req.coursId());
        d.setChapitreId(req.chapitreId());
        d.setPage(req.page());
        d.setType(req.type());
        d.setTitre(req.titre().trim());
        d.setContenu(req.contenu().trim());
        d.setAnonyme(req.anonyme());
        d.setAuteurId(user.id());
        d.setEpinglee(req.type() == TypeDiscussion.ANNONCE);
        d.setDernierActeurId(req.anonyme() ? null : user.id());
        etiquettes.affecter(d, req.etiquettes());
        discussionRepository.save(d);
        marquerLu(user.id(), d.getId());

        evenements.publishEvent(new DiscussionCreee(d.getId(), d.getCoursId(), user.id(), d.getType(), d.isAnonyme()));
        return presentation.detail(d, ctx);
    }

    @Transactional
    public DiscussionDetail modifier(Long id, DiscussionModificationRequest req, CurrentUser user) {
        Fil fil = fils.charger(id, user);
        Discussion d = fil.discussion();
        Contexte ctx = fil.ctx();
        if (!d.getAuteurId().equals(user.id()) && !user.isAdmin()) {
            throw new ForbiddenException("Seul l'auteur peut modifier cette discussion");
        }
        if (d.isVerrouillee() && !ctx.moderateur()) {
            throw new ForbiddenException("Cette discussion est verrouillée");
        }
        acces.exigerSansSanction(user);

        VersionDiscussion version = new VersionDiscussion();
        version.setDiscussionId(id);
        version.setTitre(d.getTitre());
        version.setContenu(d.getContenu());
        version.setModifieLe(d.getModifieLe() == null ? d.getCreeLe() : d.getModifieLe());
        version.setModifieParId(user.id());
        versionRepository.save(version);

        d.setTitre(req.titre().trim());
        d.setContenu(req.contenu().trim());
        d.setModifieLe(LocalDateTime.now(horloge));
        if (req.etiquettes() != null) {
            etiquettes.affecter(d, req.etiquettes());
        }
        evenements.publishEvent(new DiscussionModifiee(id, d.getCoursId()));
        return presentation.detail(d, ctx);
    }

    /** Suppression logique, par l'auteur ou un modérateur du cours (tracée dans le journal dans ce cas). */
    @Transactional
    public void supprimer(Long id, CurrentUser user) {
        Fil fil = fils.charger(id, user);
        Discussion d = fil.discussion();
        boolean auteur = d.getAuteurId().equals(user.id());
        if (!auteur && !fil.ctx().moderateur()) {
            throw new ForbiddenException("Seuls l'auteur et le formateur du cours peuvent supprimer cette discussion");
        }
        d.setSupprimeeLe(LocalDateTime.now(horloge));
        etiquettes.liberer(d);
        if (!auteur) {
            journal.tracer(user, ActionModeration.SUPPRIMER, "DISCUSSION", id, d.getCoursId(), null);
        }
    }

    // ------------------------------------------------------------------ Meilleure réponse

    /** Choisie par l'auteur de la question ou par le formateur du cours ; une seule à la fois, modifiable. */
    @Transactional
    public DiscussionDetail accepter(Long id, Long reponseId, CurrentUser user) {
        Fil fil = fils.charger(id, user);
        Discussion d = fil.discussion();
        exigerAcceptation(d, fil.ctx());
        Reponse choisie = reponseRepository.findById(reponseId)
                .filter(r -> r.getDiscussionId().equals(id) && r.getSupprimeeLe() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Réponse", reponseId));
        if (choisie.isMasquee()) {
            throw new BadRequestException("Une réponse masquée ne peut pas être choisie");
        }
        Reponse ancienne = retirerAcceptation(d);
        choisie.setAcceptee(true);
        d.setReponseAccepteeId(reponseId);
        FilService.recalculerStatut(d);
        evenements.publishEvent(new ReponseAcceptee(id, d.getCoursId(), reponseId, choisie.getAuteurId(),
                ancienne == null ? null : ancienne.getId(), ancienne == null ? null : ancienne.getAuteurId(),
                user.id(), d.getAuteurId()));
        return presentation.detail(d, fil.ctx());
    }

    @Transactional
    public DiscussionDetail retirerAcceptation(Long id, CurrentUser user) {
        Fil fil = fils.charger(id, user);
        Discussion d = fil.discussion();
        exigerAcceptation(d, fil.ctx());
        Reponse ancienne = retirerAcceptation(d);
        FilService.recalculerStatut(d);
        if (ancienne != null) {
            evenements.publishEvent(new ReponseAcceptee(id, d.getCoursId(), null, null, ancienne.getId(),
                    ancienne.getAuteurId(), user.id(), d.getAuteurId()));
        }
        return presentation.detail(d, fil.ctx());
    }

    /** Retire la réponse acceptée actuelle (sans changer le statut) et la renvoie, ou null. */
    Reponse retirerAcceptation(Discussion d) {
        if (d.getReponseAccepteeId() == null) {
            return null;
        }
        Reponse ancienne = reponseRepository.findById(d.getReponseAccepteeId()).orElse(null);
        if (ancienne != null) {
            ancienne.setAcceptee(false);
        }
        d.setReponseAccepteeId(null);
        return ancienne;
    }

    // ------------------------------------------------------------------ Aides

    @Transactional
    public void marquerLu(Long userId, Long discussionId) {
        Lecture lecture = lectureRepository.findByUserIdAndDiscussionId(userId, discussionId).orElseGet(() -> {
            Lecture l = new Lecture();
            l.setUserId(userId);
            l.setDiscussionId(discussionId);
            return l;
        });
        lecture.setDernierLuLe(LocalDateTime.now(horloge));
        lectureRepository.save(lecture);
    }

    private void exigerAcceptation(Discussion d, Contexte ctx) {
        if (d.getType() != TypeDiscussion.QUESTION) {
            throw new BadRequestException("Seule une question a une meilleure réponse");
        }
        if (!PresentationService.peutAccepter(d, ctx)) {
            throw new ForbiddenException("Seuls l'auteur de la question et le formateur du cours choisissent "
                    + "la meilleure réponse");
        }
        acces.exigerSansSanction(ctx.user());
    }

    private static Sort tri(String tri) {
        Sort epinglees = Sort.by(Sort.Direction.DESC, "epinglee");
        Sort suite = switch (tri == null ? "activite" : tri) {
            case "recentes" -> Sort.by(Sort.Direction.DESC, "creeLe");
            case "votes" -> Sort.by(Sort.Direction.DESC, "score").and(Sort.by(Sort.Direction.DESC, "creeLe"));
            case "sans-reponse" -> Sort.by(Sort.Direction.ASC, "nbReponses")
                    .and(Sort.by(Sort.Direction.DESC, "creeLe"));
            // « pertinence » n'a de sens qu'avec une recherche (q) : sans elle, activité récente.
            default -> Sort.by(Sort.Direction.DESC, "derniereActiviteLe");
        };
        return epinglees.and(suite).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    static Specification<Discussion> specification(Filtres f, CurrentUser user) {
        return (racine, requete, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isNull(racine.get("supprimeeLe")));
            p.add(cb.isFalse(racine.get("masquee")));
            if (f.coursId() != null) {
                p.add(cb.equal(racine.get("coursId"), f.coursId()));
            }
            if (f.chapitreId() != null) {
                p.add(cb.equal(racine.get("chapitreId"), f.chapitreId()));
            }
            if (f.type() != null) {
                p.add(cb.equal(racine.get("type"), f.type()));
            }
            if (f.statut() != null && !f.statut().isBlank()) {
                if ("SANS_REPONSE".equals(f.statut())) {
                    p.add(cb.equal(racine.get("nbReponses"), 0));
                    p.add(cb.notEqual(racine.get("statut"), StatutDiscussion.FERMEE));
                } else {
                    p.add(cb.equal(racine.get("statut"), statut(f.statut())));
                }
            }
            if (f.etiquette() != null && !f.etiquette().isBlank()) {
                Join<Discussion, Etiquette> e = racine.join("etiquettes");
                p.add(cb.equal(e.get("libelle"), f.etiquette().trim().toLowerCase(Locale.ROOT)));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String motif = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(racine.get("titre")), motif),
                        cb.like(cb.lower(racine.get("contenu")), motif)));
            }
            if (f.filtre() != null && !f.filtre().isBlank()) {
                switch (f.filtre()) {
                    case "miennes" -> p.add(cb.equal(racine.get("auteurId"), user.id()));
                    case "repondues" -> {
                        Subquery<Long> sq = requete.subquery(Long.class);
                        var r = sq.from(Reponse.class);
                        sq.select(r.get("id")).where(cb.equal(r.get("discussionId"), racine.get("id")),
                                cb.equal(r.get("auteurId"), user.id()), cb.isNull(r.get("supprimeeLe")));
                        p.add(cb.exists(sq));
                    }
                    case "suivies" -> {
                        Subquery<Long> sq = requete.subquery(Long.class);
                        var a = sq.from(Abonnement.class);
                        sq.select(a.get("id")).where(cb.equal(a.get("discussionId"), racine.get("id")),
                                cb.equal(a.get("userId"), user.id()));
                        p.add(cb.exists(sq));
                    }
                    case "favoris" -> {
                        Subquery<Long> sq = requete.subquery(Long.class);
                        var fav = sq.from(Favori.class);
                        sq.select(fav.get("id")).where(cb.equal(fav.get("discussionId"), racine.get("id")),
                                cb.equal(fav.get("userId"), user.id()));
                        p.add(cb.exists(sq));
                    }
                    case "non-lues" -> {
                        Subquery<Long> sq = requete.subquery(Long.class);
                        var l = sq.from(Lecture.class);
                        sq.select(l.get("id")).where(cb.equal(l.get("discussionId"), racine.get("id")),
                                cb.equal(l.get("userId"), user.id()),
                                cb.greaterThanOrEqualTo(l.get("dernierLuLe"), racine.get("derniereActiviteLe")));
                        p.add(cb.not(cb.exists(sq)));
                        p.add(cb.or(cb.isNull(racine.get("dernierActeurId")),
                                cb.notEqual(racine.get("dernierActeurId"), user.id())));
                    }
                    default -> throw new BadRequestException("Filtre inconnu : " + f.filtre());
                }
            }
            if (requete != null && f.etiquette() != null && !f.etiquette().isBlank()) {
                requete.distinct(true);
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static StatutDiscussion statut(String valeur) {
        try {
            return StatutDiscussion.valueOf(valeur);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Statut inconnu : " + valeur);
        }
    }
}

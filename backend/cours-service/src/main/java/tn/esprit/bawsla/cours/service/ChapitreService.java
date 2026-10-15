package tn.esprit.bawsla.cours.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.ChapitreRequest;
import tn.esprit.bawsla.cours.dto.ChapitreResponse;
import tn.esprit.bawsla.cours.dto.OrdreRequest;
import tn.esprit.bawsla.cours.entity.Chapitre;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.StatutCours;
import tn.esprit.bawsla.cours.repository.ChapitreRepository;
import tn.esprit.bawsla.cours.repository.ChapitreTermineRepository;

/** Chapitres d'un cours : lecture selon la visibilité du cours, écriture par le propriétaire ou l'ADMIN. */
@Service
@Transactional(readOnly = true)
public class ChapitreService {

    private final CoursService coursService;
    private final ChapitreRepository chapitreRepository;
    private final ChapitreTermineRepository termineRepository;

    public ChapitreService(CoursService coursService, ChapitreRepository chapitreRepository,
                           ChapitreTermineRepository termineRepository) {
        this.coursService = coursService;
        this.chapitreRepository = chapitreRepository;
        this.termineRepository = termineRepository;
    }

    public List<ChapitreResponse> lister(Long coursId, CurrentUser user) {
        return coursService.chargerVisible(coursId, user).getChapitres().stream().map(ChapitreResponse::from).toList();
    }

    @Transactional
    public ChapitreResponse creer(Long coursId, ChapitreRequest request, CurrentUser user) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        Chapitre chapitre = new Chapitre();
        chapitre.setCours(cours);
        chapitre.setOrdre(cours.getChapitres().size() + 1);
        appliquer(chapitre, request);
        cours.getChapitres().add(chapitre);
        return ChapitreResponse.from(chapitreRepository.save(chapitre));
    }

    @Transactional
    public ChapitreResponse modifier(Long coursId, Long chapitreId, ChapitreRequest request, CurrentUser user) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        Chapitre chapitre = trouver(cours, chapitreId);
        appliquer(chapitre, request);
        return ChapitreResponse.from(chapitre);
    }

    @Transactional
    public void supprimer(Long coursId, Long chapitreId, CurrentUser user) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        Chapitre chapitre = trouver(cours, chapitreId);
        if (cours.getStatut() == StatutCours.PUBLIE && cours.getChapitres().size() == 1) {
            throw new BadRequestException("Un cours publié doit garder au moins un chapitre : dépublie-le d'abord.");
        }
        cours.getChapitres().remove(chapitre);
        termineRepository.supprimerDuChapitre(chapitreId); // les apprenants qui l'avaient terminé ne le comptent plus
        int ordre = 1;
        for (Chapitre restant : cours.getChapitres()) {
            restant.setOrdre(ordre++);
        }
    }

    /** Nouvel ordre : la liste doit contenir exactement les chapitres du cours, une fois chacun. */
    @Transactional
    public List<ChapitreResponse> reordonner(Long coursId, OrdreRequest request, CurrentUser user) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        List<Long> ids = request.ids();
        Set<Long> attendus = cours.getChapitres().stream().map(Chapitre::getId).collect(Collectors.toSet());
        if (ids.size() != attendus.size() || !attendus.equals(new HashSet<>(ids))) {
            throw new BadRequestException("La liste doit contenir chacun des " + attendus.size()
                    + " chapitres du cours, une seule fois.");
        }
        Map<Long, Chapitre> parId = cours.getChapitres().stream().collect(Collectors.toMap(Chapitre::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            parId.get(ids.get(i)).setOrdre(i + 1);
        }
        cours.getChapitres().sort((a, b) -> Integer.compare(a.getOrdre(), b.getOrdre()));
        return cours.getChapitres().stream().map(ChapitreResponse::from).toList();
    }

    private static Chapitre trouver(Cours cours, Long chapitreId) {
        return cours.getChapitres().stream()
                .filter(c -> c.getId().equals(chapitreId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Chapitre", chapitreId));
    }

    private static void appliquer(Chapitre chapitre, ChapitreRequest r) {
        chapitre.setTitre(r.titre().strip());
        chapitre.setContenu(r.contenu());
        chapitre.setDureeMinutes(r.dureeMinutes() == null ? 0 : r.dureeMinutes());
        chapitre.setVideoUrl(r.videoUrl() == null || r.videoUrl().isBlank() ? null : r.videoUrl().strip());
    }
}

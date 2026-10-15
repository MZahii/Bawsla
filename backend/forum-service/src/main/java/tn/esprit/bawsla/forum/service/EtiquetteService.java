package tn.esprit.bawsla.forum.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.EtiquetteResponse;
import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.Etiquette;
import tn.esprit.bawsla.forum.repository.EtiquetteRepository;

/** Étiquettes partagées par toute la plateforme : création à la volée, compteur d'utilisations, autocomplétion. */
@Service
@Transactional(readOnly = true)
public class EtiquetteService {

    private static final int SUGGESTIONS = 10;

    private final EtiquetteRepository repository;
    private final AccesService acces;

    public EtiquetteService(EtiquetteRepository repository, AccesService acces) {
        this.repository = repository;
        this.acces = acces;
    }

    /** Autocomplétion : d'abord les étiquettes du cours, sinon celles de la plateforme. */
    public List<EtiquetteResponse> rechercher(Long coursId, String q, CurrentUser user) {
        acces.exigerConnecte(user);
        String prefixe = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        PageRequest page = PageRequest.of(0, SUGGESTIONS);
        List<Etiquette> trouvees = coursId == null ? List.of()
                : repository.rechercherDansCours(coursId, prefixe, page);
        if (trouvees.isEmpty()) {
            trouvees = repository.rechercher(prefixe, page);
        }
        return trouvees.stream().map(EtiquetteResponse::from).toList();
    }

    /** Remplace les étiquettes d'une discussion (libellés bruts, normalisés ici) et tient les compteurs à jour. */
    @Transactional
    public void affecter(Discussion d, List<String> libellesBruts) {
        List<String> libelles = Textes.etiquettes(libellesBruts);
        Set<Etiquette> anciennes = new LinkedHashSet<>(d.getEtiquettes());
        Set<Etiquette> nouvelles = new LinkedHashSet<>();
        for (String libelle : libelles) {
            nouvelles.add(repository.findByLibelle(libelle).orElseGet(() -> {
                Etiquette e = new Etiquette();
                e.setLibelle(libelle);
                return repository.save(e);
            }));
        }
        anciennes.stream().filter(e -> !nouvelles.contains(e))
                .forEach(e -> e.setNbUtilisations(Math.max(0, e.getNbUtilisations() - 1)));
        nouvelles.stream().filter(e -> !anciennes.contains(e))
                .forEach(e -> e.setNbUtilisations(e.getNbUtilisations() + 1));
        d.getEtiquettes().clear();
        d.getEtiquettes().addAll(nouvelles);
    }

    /** Une discussion supprimée ne compte plus dans l'utilisation de ses étiquettes. */
    @Transactional
    public void liberer(Discussion d) {
        d.getEtiquettes().forEach(e -> e.setNbUtilisations(Math.max(0, e.getNbUtilisations() - 1)));
    }
}

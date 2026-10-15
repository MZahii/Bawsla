package tn.esprit.bawsla.cours.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.PageDocument;
import tn.esprit.bawsla.cours.entity.SourcePage;
import tn.esprit.bawsla.cours.repository.CoursRepository;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;
import tn.esprit.bawsla.cours.repository.TraitementIARepository;

/** Écritures courtes de la lecture par vision, chacune dans sa transaction : le traitement asynchrone n'en garde aucune pendant l'attente de l'IA. */
@Service
public class VisionEnregistrement {

    /** Ce qu'il faut pour lire une page : le fichier de l'image déposée, ou le PDF dont on rend la page. */
    public record Cible(String imageNom, String pdfNom) {
    }

    private final PageDocumentRepository pageRepository;
    private final CoursRepository coursRepository;
    private final TraitementIARepository traitementRepository;

    public VisionEnregistrement(PageDocumentRepository pageRepository, CoursRepository coursRepository,
                                TraitementIARepository traitementRepository) {
        this.pageRepository = pageRepository;
        this.coursRepository = coursRepository;
        this.traitementRepository = traitementRepository;
    }

    /** Vide si la page a disparu entre-temps (image supprimée pendant la lecture). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Cible> cible(Long coursId, int numero) {
        return pageRepository.findByCoursIdAndNumero(coursId, numero)
                .map(p -> new Cible(p.getImageStockage(), coursRepository.findById(coursId).map(c -> c.getPdfStockage()).orElse(null)));
    }

    /** Enregistre le texte lu. Une page « illisible » reste vide mais est marquée lue : elle ne sera pas relue (et repayée) au lancement suivant. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerTexte(Long coursId, int numero, String texte, String moteur) {
        pageRepository.findByCoursIdAndNumero(coursId, numero).ifPresent((PageDocument p) -> {
            p.setTexte(texte == null ? "" : texte.strip());
            p.setSource(SourcePage.VISION);
            p.setMoteur(moteur);
            p.setModifie(false);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void avancer(String execution, Long coursId, int fait, int total) {
        traitementRepository.findByCoursIdAndExecutionOrderByIdAsc(coursId, execution).stream()
                .filter(t -> t.getEtape() == EtapeIA.LECTURE_IMAGES)
                .findFirst()
                .ifPresent(t -> {
                    t.setAvancement(fait);
                    t.setTotal(total);
                });
    }
}

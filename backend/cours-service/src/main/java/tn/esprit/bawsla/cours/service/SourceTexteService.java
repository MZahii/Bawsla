package tn.esprit.bawsla.cours.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.entity.Chapitre;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.PageDocument;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;

/**
 * Texte d'un cours à confier à l'IA : celui du PDF s'il en a un (avec du texte), sinon le contenu des chapitres.
 * Les emails et téléphones sont masqués, et le texte ne contient jamais de donnée sur un utilisateur.
 */
@Service
@Transactional(readOnly = true)
public class SourceTexteService {

    static final int MINIMUM = 50;
    /** Limite acceptée par ai-service. */
    static final int MAXIMUM = 300_000;

    private final PageDocumentRepository pageRepository;

    public SourceTexteService(PageDocumentRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    /** @throws BadRequestException si le cours n'a ni PDF avec du texte, ni chapitre avec du contenu */
    public String pour(Cours cours) {
        if (cours.getPdfStockage() != null) {
            String pdf = pageRepository.findByCoursIdOrderByNumeroAsc(cours.getId()).stream()
                    .map(PageDocument::getTexte)
                    .map(TexteMasker::masquer)
                    .filter(t -> !t.isBlank())
                    .collect(Collectors.joining("\n\n"));
            if (pdf.strip().length() >= MINIMUM) {
                return limiter(pdf);
            }
        }
        List<Chapitre> chapitres = cours.getChapitres();
        String contenu = chapitres.stream()
                .filter(c -> c.getContenu() != null && !c.getContenu().isBlank())
                .map(c -> "Chapitre " + c.getOrdre() + " : " + c.getTitre() + "\n" + TexteMasker.masquer(c.getContenu()))
                .collect(Collectors.joining("\n\n"));
        if (contenu.strip().length() >= MINIMUM) {
            return limiter(contenu);
        }
        throw new BadRequestException("Impossible de générer le résumé : ajoute un PDF contenant du texte "
                + "(pas un scan) ou du contenu dans les chapitres du cours.");
    }

    private static String limiter(String texte) {
        String propre = texte.strip();
        return propre.length() <= MAXIMUM ? propre : propre.substring(0, MAXIMUM);
    }
}

package tn.esprit.bawsla.cours.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.client.PageIaDto;
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

    /** Pages (ou chapitres) du cours avec leur numéro. {@code chapitres} : le texte vient des chapitres, pas d'un PDF. */
    public record PagesSource(List<PageIaDto> pages, boolean chapitres) {
    }

    private final PageDocumentRepository pageRepository;
    private final int minimumExplication;

    public SourceTexteService(PageDocumentRepository pageRepository,
                              @Value("${cours.explication.texte-min-caracteres:200}") int minimumExplication) {
        this.pageRepository = pageRepository;
        this.minimumExplication = minimumExplication;
    }

    /** @throws BadRequestException si le cours n'a ni PDF avec du texte, ni chapitre avec du contenu */
    public String pour(Cours cours) {
        {
            // Pages du PDF et pages lues par vision (PDF scanné, images déposées) : toutes sont dans PageDocument.
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

    /**
     * Texte du cours page par page, pour l'explication orale (emails et téléphones masqués). Pages du PDF si elles contiennent du texte,
     * sinon un « page » par chapitre qui a du contenu. La limite de caractères envoyés à l'IA est appliquée côté ai-service,
     * qui garde en priorité les pages des notions du plan.
     *
     * @throws BadRequestException si le total est sous le minimum (cours vide, PDF scanné) : aucun appel à l'IA n'est alors fait
     */
    public PagesSource pagesPour(Cours cours) {
        boolean pdfSansTexte = false;
        {
            List<PageIaDto> pages = pageRepository.findByCoursIdOrderByNumeroAsc(cours.getId()).stream()
                    .map(p -> new PageIaDto(p.getNumero(), TexteMasker.masquer(p.getTexte()).strip()))
                    .filter(p -> !p.texte().isBlank())
                    .toList();
            if (total(pages) >= minimumExplication) {
                return new PagesSource(pages, false);
            }
            pdfSansTexte = cours.getPdfStockage() != null && !pageRepository.findByCoursIdOrderByNumeroAsc(cours.getId()).isEmpty();
            // un PDF d'images : l'extraction ne trouve (presque) rien
        }
        List<PageIaDto> chapitres = new ArrayList<>();
        for (Chapitre c : cours.getChapitres()) {
            if (c.getContenu() != null && !c.getContenu().isBlank()) {
                chapitres.add(new PageIaDto(c.getOrdre(), "Chapitre " + c.getOrdre() + " : " + c.getTitre() + "\n"
                        + TexteMasker.masquer(c.getContenu()).strip()));
            }
        }
        if (total(chapitres) >= minimumExplication) {
            return new PagesSource(chapitres, true);
        }
        throw new BadRequestException(pdfSansTexte
                ? "Ce PDF semble scanné : il ne contient pas assez de texte pour que l'IA puisse expliquer le cours. "
                        + "Envoie un PDF avec du texte sélectionnable ou ajoute du contenu dans les chapitres."
                : "Ce cours n'a pas assez de texte à expliquer : ajoute un PDF contenant du texte ou du contenu dans les chapitres.");
    }

    private static int total(List<PageIaDto> pages) {
        return pages.stream().mapToInt(p -> p.texte().length()).sum();
    }

    private static String limiter(String texte) {
        String propre = texte.strip();
        return propre.length() <= MAXIMUM ? propre : propre.substring(0, MAXIMUM);
    }
}

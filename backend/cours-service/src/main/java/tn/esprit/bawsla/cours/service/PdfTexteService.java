package tn.esprit.bawsla.cours.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;

/** Extraction du texte d'un PDF, page par page (Apache PDFBox). */
@Component
public class PdfTexteService {

    static final int PAGES_MAX = 1000;

    /**
     * @return le texte de chaque page (chaîne vide pour une page sans texte, par exemple un scan)
     * @throws BadRequestException si le fichier n'est pas un PDF lisible (corrompu, protégé par mot de passe, trop long)
     */
    public List<String> extrairePages(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int nbPages = document.getNumberOfPages();
            if (nbPages > PAGES_MAX) {
                throw new BadRequestException("PDF trop long : " + PAGES_MAX + " pages maximum");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>(nbPages);
            for (int numero = 1; numero <= nbPages; numero++) {
                stripper.setStartPage(numero);
                stripper.setEndPage(numero);
                pages.add(nettoyer(stripper.getText(document)));
            }
            return pages;
        } catch (IOException e) {
            throw new BadRequestException("PDF illisible : fichier corrompu ou protégé par un mot de passe");
        }
    }

    private static String nettoyer(String texte) {
        return texte == null ? "" : texte.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]", "").strip();
    }
}

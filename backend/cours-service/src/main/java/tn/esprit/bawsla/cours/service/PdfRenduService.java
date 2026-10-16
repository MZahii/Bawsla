package tn.esprit.bawsla.cours.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;

/** Rend une page de PDF en image PNG (PDFBox, 150 dpi) pour la lire par vision. Le PDF n'est ouvert qu'une fois par lecture. */
@Component
public class PdfRenduService {

    /** Résolution du rendu : assez fine pour du texte courant, assez légère pour rester sous quelques Mo par page. */
    public static final float DPI = 150f;

    /** Un PDF ouvert pour être rendu page par page. À fermer après usage. */
    public static final class Rendu implements AutoCloseable {

        private final PDDocument document;
        private final PDFRenderer renderer;

        private Rendu(PDDocument document) {
            this.document = document;
            this.renderer = new PDFRenderer(document);
        }

        public int nbPages() {
            return document.getNumberOfPages();
        }

        /** PNG de la page {@code numero} (à partir de 1). */
        public byte[] png(int numero) {
            if (numero < 1 || numero > nbPages()) {
                throw new BadRequestException("Page " + numero + " introuvable dans le PDF.");
            }
            try {
                BufferedImage image = renderer.renderImageWithDPI(numero - 1, DPI, ImageType.RGB);
                ByteArrayOutputStream sortie = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", sortie)) {
                    throw new IOException("Encodeur PNG indisponible");
                }
                return sortie.toByteArray();
            } catch (IOException e) {
                throw new BadRequestException("La page " + numero + " du PDF ne peut pas être rendue en image.");
            }
        }

        @Override
        public void close() {
            try {
                document.close();
            } catch (IOException ignore) {
                // fermeture silencieuse : rien d'autre à faire
            }
        }
    }

    public Rendu ouvrir(byte[] pdf) {
        System.setProperty("java.awt.headless", "true"); // aucun écran sur le serveur
        try {
            return new Rendu(Loader.loadPDF(pdf));
        } catch (IOException e) {
            throw new BadRequestException("PDF illisible : fichier corrompu ou protégé par un mot de passe");
        }
    }
}

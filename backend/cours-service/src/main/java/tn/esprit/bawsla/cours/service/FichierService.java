package tn.esprit.bawsla.cours.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CoursResponse;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.PageDocument;
import tn.esprit.bawsla.cours.repository.PageDocumentRepository;
import tn.esprit.bawsla.cours.service.StockageService.Type;

/**
 * Image de couverture (JPG ou PNG, 2 Mo) et PDF (20 Mo) d'un cours. Le vrai type est lu dans le contenu du fichier,
 * jamais déduit du nom ou du Content-Type envoyé par le client.
 */
@Service
@Transactional(readOnly = true)
public class FichierService {

    static final long IMAGE_MAX = 2L * 1024 * 1024;
    static final long PDF_MAX = 20L * 1024 * 1024;

    private static final Pattern CARACTERES_INTERDITS = Pattern.compile("[^\\p{L}\\p{N} ._()\\-]");

    private final CoursService coursService;
    private final StockageService stockage;
    private final PdfTexteService extracteur;
    private final PageDocumentRepository pageRepository;

    public FichierService(CoursService coursService, StockageService stockage, PdfTexteService extracteur,
                          PageDocumentRepository pageRepository) {
        this.coursService = coursService;
        this.stockage = stockage;
        this.extracteur = extracteur;
        this.pageRepository = pageRepository;
    }

    /** Fichier servi avec son type MIME et un nom pour le téléchargement. */
    public record Fichier(Resource ressource, String typeMime, String nom) {
    }

    // ------------------------------------------------------------------ image

    @Transactional
    public CoursResponse deposerImage(Long coursId, MultipartFile envoi, CurrentUser user) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        byte[] octets = lire(envoi, IMAGE_MAX, "L'image dépasse 2 Mo");
        String extension = extensionImage(octets);
        String ancien = cours.getImageFichier();
        cours.setImageFichier(stockage.sauvegarder(Type.IMAGES, octets, extension));
        stockage.supprimerApresValidation(Type.IMAGES, ancien);
        return coursService.reponse(cours);
    }

    /** {@code user} peut être null (visiteur) : seule l'image d'un cours publié est alors servie. */
    public Fichier image(Long coursId, CurrentUser user) {
        Cours cours = coursService.chargerVisible(coursId, user);
        if (cours.getImageFichier() == null) {
            throw new ResourceNotFoundException("Image du cours", coursId);
        }
        String mime = cours.getImageFichier().endsWith(".png") ? "image/png" : "image/jpeg";
        return new Fichier(stockage.lire(Type.IMAGES, cours.getImageFichier()), mime, cours.getImageFichier());
    }

    // ------------------------------------------------------------------ PDF

    @Transactional
    public CoursResponse deposerPdf(Long coursId, MultipartFile envoi, CurrentUser user) {
        Cours cours = coursService.chargerPourEcriture(coursId, user);
        byte[] octets = lire(envoi, PDF_MAX, "Le PDF dépasse 20 Mo");
        if (!ressembleAUnPdf(octets)) {
            throw new BadRequestException("Ce fichier n'est pas un PDF valide.");
        }
        List<String> pages = extracteur.extrairePages(octets);

        String ancien = cours.getPdfStockage();
        cours.setPdfStockage(stockage.sauvegarder(Type.PDF, octets, "pdf"));
        cours.setFichierPdf(nomAffichable(envoi.getOriginalFilename()));
        cours.setPdfTaille((long) octets.length);
        stockage.supprimerApresValidation(Type.PDF, ancien);

        // Seules les pages du PDF sont remplacées : les images déposées par le formateur restent, renumérotées à la suite.
        List<PageDocument> images = pageRepository.findByCoursIdAndImageStockageIsNotNullOrderByNumeroAsc(coursId);
        pageRepository.supprimerPagesPdf(coursId);
        List<PageDocument> documents = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            documents.add(new PageDocument(coursId, i + 1, pages.get(i)));
        }
        pageRepository.saveAll(documents);
        for (int i = 0; i < images.size(); i++) {
            images.get(i).setNumero(pages.size() + 1 + i);
        }
        return coursService.reponse(cours);
    }

    /** Propriétaire, ADMIN, ou tout utilisateur si le cours est publié. */
    public Fichier pdf(Long coursId, CurrentUser user) {
        Cours cours = coursService.chargerLisible(coursId, user);
        if (cours.getPdfStockage() == null) {
            throw new ResourceNotFoundException("PDF du cours", coursId);
        }
        String nom = cours.getFichierPdf() != null ? cours.getFichierPdf() : "cours-" + coursId + ".pdf";
        return new Fichier(stockage.lire(Type.PDF, cours.getPdfStockage()), "application/pdf", nom);
    }

    // ------------------------------------------------------------------ vérifications

    static byte[] lire(MultipartFile envoi, long max, String messageTropGros) {
        if (envoi == null || envoi.isEmpty()) {
            throw new BadRequestException("Aucun fichier reçu (champ « fichier »).");
        }
        if (envoi.getSize() > max) {
            throw new BadRequestException(messageTropGros + ".");
        }
        try {
            return envoi.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Fichier illisible.");
        }
    }

    /** JPEG (FF D8 FF) ou PNG (signature de 8 octets), puis décodage réel de l'image. */
    static String extensionImage(byte[] o) {
        String extension;
        if (o.length > 3 && (o[0] & 0xFF) == 0xFF && (o[1] & 0xFF) == 0xD8 && (o[2] & 0xFF) == 0xFF) {
            extension = "jpg";
        } else if (o.length > 8 && (o[0] & 0xFF) == 0x89 && o[1] == 'P' && o[2] == 'N' && o[3] == 'G'
                && o[4] == 0x0D && o[5] == 0x0A && o[6] == 0x1A && o[7] == 0x0A) {
            extension = "png";
        } else {
            throw new BadRequestException("Format d'image non pris en charge : JPG ou PNG uniquement.");
        }
        try {
            if (ImageIO.read(new ByteArrayInputStream(o)) == null) {
                throw new BadRequestException("Image illisible ou corrompue.");
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) {
                throw bad;
            }
            throw new BadRequestException("Image illisible ou corrompue.");
        }
        return extension;
    }

    /** Un vrai PDF commence par « %PDF- » (tolérance de 1 Ko d'en-tête, comme les lecteurs courants). */
    private static boolean ressembleAUnPdf(byte[] o) {
        int limite = Math.min(o.length, 1024);
        String debut = new String(o, 0, limite, StandardCharsets.ISO_8859_1);
        return debut.contains("%PDF-");
    }

    /** Nom d'origine nettoyé, pour l'affichage uniquement. */
    private static String nomAffichable(String original) {
        if (original == null || original.isBlank()) {
            return "cours.pdf";
        }
        String nom = original.replace('\\', '/');
        nom = nom.substring(nom.lastIndexOf('/') + 1);
        nom = CARACTERES_INTERDITS.matcher(nom).replaceAll("_").strip();
        if (nom.length() > 150) {
            nom = nom.substring(nom.length() - 150);
        }
        return nom.isEmpty() ? "cours.pdf" : nom;
    }
}

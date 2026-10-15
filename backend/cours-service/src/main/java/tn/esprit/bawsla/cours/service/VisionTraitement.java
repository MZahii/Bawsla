package tn.esprit.bawsla.cours.service;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import tn.esprit.bawsla.cours.client.VisionIaDto;
import tn.esprit.bawsla.cours.client.VisionIaRequestDto;
import tn.esprit.bawsla.cours.common.exception.BawslaException;
import tn.esprit.bawsla.cours.common.exception.BadRequestException;
import tn.esprit.bawsla.cours.config.AsyncConfig;
import tn.esprit.bawsla.cours.entity.EtapeIA;
import tn.esprit.bawsla.cours.entity.StatutTraitement;
import tn.esprit.bawsla.cours.service.StockageService.Type;
import tn.esprit.bawsla.cours.service.VisionEnregistrement.Cible;

/**
 * Lecture des pages sans texte par vision, en arrière-plan : chaque page est rendue en image (PDF scanné, PDFBox à 150 dpi) ou relue sur
 * disque (image déposée), envoyée à l'IA, puis son texte est enregistré. Le {@code Authorization} de l'utilisateur est capturé par le
 * contrôleur AVANT le passage en asynchrone. Au premier échec (quota, indisponibilité), la lecture s'arrête : les pages déjà lues sont
 * conservées et le lancement suivant ne reprend que les pages encore vides.
 *
 * <p>Quand Gemini est indisponible, l'ai-service lit la page avec son modèle local (fournisseur « local … »), bien plus lent. Dès qu'une
 * page revient du local, les suivantes du même lancement sont demandées directement en local ({@code forcerLocal}) : on ne perd pas le
 * délai de Gemini à chaque page. Le lancement s'arrête après {@code vision-locale-pages-max} pages lues en local ; le formateur relance.</p>
 */
@Component
public class VisionTraitement {

    /** Début du nom du fournisseur renvoyé par l'ai-service quand la page a été lue par le modèle local (« local · Florence-2 »). */
    static final String FOURNISSEUR_LOCAL = "local";

    private static final Logger log = LoggerFactory.getLogger(VisionTraitement.class);

    private final IaGateway ia;
    private final StockageService stockage;
    private final PdfRenduService rendu;
    private final FicheEnregistrement suivi;
    private final VisionEnregistrement enregistrement;
    private final int localMax;

    public VisionTraitement(IaGateway ia, StockageService stockage, PdfRenduService rendu, FicheEnregistrement suivi,
                            VisionEnregistrement enregistrement, @Value("${cours.ia.vision-locale-pages-max:10}") int localMax) {
        this.ia = ia;
        this.stockage = stockage;
        this.rendu = rendu;
        this.suivi = suivi;
        this.enregistrement = enregistrement;
        this.localMax = Math.max(1, localMax);
    }

    @Async(AsyncConfig.IA_EXECUTOR)
    public void lire(String execution, Long coursId, List<Integer> numeros, String authorization) {
        PdfRenduService.Rendu pdf = null;
        try {
            suivi.passer(execution, coursId, EtapeIA.LECTURE_IMAGES, StatutTraitement.EN_COURS, null);
            int fait = 0;
            int locales = 0;
            boolean local = false;
            String reste = null;
            for (int numero : numeros) {
                if (locales >= localMax) {
                    int restantes = numeros.size() - fait;
                    reste = restantes + (restantes > 1 ? " pages restantes" : " page restante") + " : clique à nouveau sur Lire avec l'IA";
                    break;
                }
                Cible cible = enregistrement.cible(coursId, numero).orElse(null);
                if (cible != null) {
                    byte[] image;
                    String mime;
                    if (cible.imageNom() != null) {
                        image = octets(Type.PAGES, cible.imageNom());
                        mime = cible.imageNom().endsWith(".png") ? "image/png" : "image/jpeg";
                    } else {
                        if (pdf == null) {
                            if (cible.pdfNom() == null) {
                                throw new BadRequestException("Le PDF de ce cours est introuvable.");
                            }
                            pdf = rendu.ouvrir(octets(Type.PDF, cible.pdfNom()));
                        }
                        image = pdf.png(numero);
                        mime = "image/png";
                    }
                    VisionIaDto lu = ia.vision(authorization,
                            new VisionIaRequestDto(coursId, numero, Base64.getEncoder().encodeToString(image), mime, local));
                    enregistrement.enregistrerTexte(coursId, numero, lu.texte(), lu.fournisseur());
                    if (lu.fournisseur() != null && lu.fournisseur().startsWith(FOURNISSEUR_LOCAL)) {
                        local = true;
                        locales++;
                    }
                }
                fait++;
                enregistrement.avancer(execution, coursId, fait, numeros.size());
            }
            suivi.passer(execution, coursId, EtapeIA.LECTURE_IMAGES, StatutTraitement.TERMINE, reste);
        } catch (RuntimeException e) {
            String message = e instanceof BawslaException ? e.getMessage() : "Erreur inattendue pendant la lecture des pages.";
            log.warn("Lecture des pages du cours {} en échec : {}", coursId, e.getClass().getSimpleName());
            try {
                suivi.passer(execution, coursId, EtapeIA.LECTURE_IMAGES, StatutTraitement.ECHEC, message);
            } catch (RuntimeException ignore) {
                // le suivi lui-même est indisponible : l'étape restera « en cours » jusqu'au redémarrage
            }
        } finally {
            if (pdf != null) {
                pdf.close();
            }
        }
    }

    private byte[] octets(Type type, String nom) {
        try {
            return stockage.lire(type, nom).getContentAsByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Fichier illisible.");
        }
    }
}

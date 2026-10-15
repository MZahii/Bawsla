package tn.esprit.bawsla.cours.dto;

import tn.esprit.bawsla.cours.entity.PageDocument;
import tn.esprit.bawsla.cours.entity.SourcePage;
import tn.esprit.bawsla.cours.service.PagesLuesService;

/**
 * Une page du cours telle que le formateur la voit : son texte (extrait du PDF ou lu par l'IA), sa source et ce qu'il reste à faire.
 * {@code sansTexte} : moins de 30 caractères. {@code aLire} : la lecture par vision peut encore y trouver du texte.
 * {@code aImage} : page déposée sous forme d'image (aperçu disponible). {@code moteur} : moteur réel de la lecture par vision
 * (« gemini », « local · Florence-2 »), absent si la page n'a pas été lue par l'IA ou si le formateur a corrigé le texte.
 */
public record PageLueResponse(int numero, String texte, SourcePage source, boolean sansTexte, boolean modifie, boolean aImage, boolean aLire, String moteur) {

    public static PageLueResponse from(PageDocument p) {
        return new PageLueResponse(p.getNumero(), p.getTexte() == null ? "" : p.getTexte(), p.getSource(), PagesLuesService.sansTexte(p),
                p.isModifie(), p.getImageStockage() != null, PagesLuesService.aLire(p), p.isModifie() ? null : p.getMoteur());
    }
}

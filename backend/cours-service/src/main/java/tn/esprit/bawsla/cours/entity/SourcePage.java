package tn.esprit.bawsla.cours.entity;

/** D'où vient le texte d'une page : extrait du PDF par PDFBox, ou lu par l'IA (vision) sur une page scannée ou une image. */
public enum SourcePage {
    PDFBOX,
    VISION
}

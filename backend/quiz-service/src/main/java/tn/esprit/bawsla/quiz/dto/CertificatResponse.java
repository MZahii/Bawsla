package tn.esprit.bawsla.quiz.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.quiz.entity.Certificat;

/** etudiantId est omis (null) dans la vérification d'un numéro. */
public record CertificatResponse(String numero, Long coursId, Long quizId, String titreExamen, int score,
                                 LocalDateTime dateEmission, Long etudiantId) {

    public static CertificatResponse from(Certificat c) {
        return new CertificatResponse(c.getNumero(), c.getCoursId(), c.getQuizId(), c.getTitreExamen(), c.getScore(),
                c.getDateEmission(), c.getEtudiantId());
    }

    public static CertificatResponse verification(Certificat c) {
        return new CertificatResponse(c.getNumero(), c.getCoursId(), c.getQuizId(), c.getTitreExamen(), c.getScore(),
                c.getDateEmission(), null);
    }
}

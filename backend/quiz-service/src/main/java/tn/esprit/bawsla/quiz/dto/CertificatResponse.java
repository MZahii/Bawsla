package tn.esprit.bawsla.quiz.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import tn.esprit.bawsla.quiz.entity.Certificat;

/**
 * etudiantId vaut null dans la vérification publique d'un numéro ; NON_NULL le retire du JSON
 * (ApiResponse n'applique NON_NULL qu'à ses propres champs, pas à data).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

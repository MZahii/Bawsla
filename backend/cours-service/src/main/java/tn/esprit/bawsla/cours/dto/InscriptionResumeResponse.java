package tn.esprit.bawsla.cours.dto;

/** Contrat 8 : une inscription réduite à ce qu'il faut pour le filtrage collaboratif du module User (aucune donnée personnelle). */
public record InscriptionResumeResponse(Long etudiantId, Long coursId, double progression, boolean termine) {

    public static InscriptionResumeResponse from(InscriptionResponse i) {
        return new InscriptionResumeResponse(i.etudiantId(), i.coursId(), i.progression(), i.termine());
    }
}

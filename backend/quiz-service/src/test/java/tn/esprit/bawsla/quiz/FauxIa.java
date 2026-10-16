package tn.esprit.bawsla.quiz;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import tn.esprit.bawsla.quiz.client.IaClient;
import tn.esprit.bawsla.quiz.client.RemedierIaDto;
import tn.esprit.bawsla.quiz.client.RemedierIaDto.ChoixIaDto;
import tn.esprit.bawsla.quiz.client.RemedierIaDto.QuestionIaDto;
import tn.esprit.bawsla.quiz.client.RemedierIaRequestDto;
import tn.esprit.bawsla.quiz.common.ApiResponse;

/** Remplace l'appel à la gateway : réponse fixe, compte les appels et garde les corps envoyés. */
class FauxIa implements IaClient {

    final AtomicInteger appels = new AtomicInteger();
    final List<RemedierIaRequestDto> corps = new ArrayList<>();
    final List<String> jetons = new ArrayList<>();

    @Override
    public ApiResponse<RemedierIaDto> remedier(String authorization, RemedierIaRequestDto requete) {
        appels.incrementAndGet();
        corps.add(requete);
        jetons.add(authorization);
        QuestionIaDto rattrapage = new QuestionIaDto("Combien de lignes renvoie une jointure interne sans correspondance ?",
                "APPLIQUER", requete.notion(),
                List.of(new ChoixIaDto("Aucune", true, null),
                        new ChoixIaDto("Toutes les lignes de gauche", false, "Confusion avec LEFT JOIN"),
                        new ChoixIaDto("Le produit cartésien", false, "Confusion avec CROSS JOIN")),
                "Sans correspondance, la jointure interne ne garde rien.", "VALIDE", null);
        return ApiResponse.ok(new RemedierIaDto(requete.notion(), "raisonnement interne", "Explication de test",
                "Revois la différence entre INNER et LEFT JOIN.", rattrapage, "remedier-v1", "gemini", List.of()));
    }
}

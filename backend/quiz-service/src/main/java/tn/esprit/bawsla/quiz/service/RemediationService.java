package tn.esprit.bawsla.quiz.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;
import tn.esprit.bawsla.quiz.client.IaClient;
import tn.esprit.bawsla.quiz.client.RemedierIaDto;
import tn.esprit.bawsla.quiz.client.RemedierIaDto.ChoixIaDto;
import tn.esprit.bawsla.quiz.client.RemedierIaDto.QuestionIaDto;
import tn.esprit.bawsla.quiz.client.RemedierIaRequestDto;
import tn.esprit.bawsla.quiz.common.ApiResponse;
import tn.esprit.bawsla.quiz.common.exception.BadRequestException;
import tn.esprit.bawsla.quiz.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.quiz.common.exception.ServiceUnavailableException;
import tn.esprit.bawsla.quiz.common.security.CurrentUser;
import tn.esprit.bawsla.quiz.dto.RattrapageRequest;
import tn.esprit.bawsla.quiz.dto.RattrapageResultat;
import tn.esprit.bawsla.quiz.dto.RemediationResponse;
import tn.esprit.bawsla.quiz.dto.RemediationResponse.QuestionRattrapage;
import tn.esprit.bawsla.quiz.entity.NotionRatee;
import tn.esprit.bawsla.quiz.entity.Option;
import tn.esprit.bawsla.quiz.entity.Question;
import tn.esprit.bawsla.quiz.entity.Reponse;
import tn.esprit.bawsla.quiz.repository.NotionRateeRepository;

/**
 * Remédiation d'une mauvaise réponse par l'IA (POST /api/ai/quiz/remedier, via la gateway) : diagnostic, conseil et
 * question de rattrapage, enregistrés dans la NotionRatee. Un seul appel IA par erreur (les suivants relisent la base).
 * Seuls la question et les deux réponses partent vers l'IA : aucun identifiant ni nom d'apprenant.
 * La question de rattrapage est renvoyée sans ses bonnes réponses ; la correction se fait ici.
 */
@Service
@Transactional(readOnly = true)
public class RemediationService {

    private static final String BLOOM_PAR_DEFAUT = "COMPRENDRE";
    private static final String SANS_REPONSE = "(aucune réponse)";

    private final NotionRateeRepository notionRateeRepository;
    private final IaClient iaClient;
    private final ObjectMapper mapper;

    public RemediationService(NotionRateeRepository notionRateeRepository, IaClient iaClient, ObjectMapper mapper) {
        this.notionRateeRepository = notionRateeRepository;
        this.iaClient = iaClient;
        this.mapper = mapper;
    }

    @Transactional
    public RemediationResponse remedier(Long reponseId, String authorization, CurrentUser user) {
        NotionRatee notion = notionRateeRepository.findByReponseId(reponseId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune erreur à expliquer pour la réponse " + reponseId));
        user.requireOwnerOrAdmin(notion.getEtudiantId());
        List<String> alertes = List.of();
        if (notion.getRattrapageJson() == null && notion.getExplicationIa() == null) {
            RemedierIaDto ia = appelerIa(notion, authorization);
            notion.setRaisonnement(ia.raisonnement());
            notion.setExplicationIa(ia.explication());
            notion.setConseil(ia.conseil());
            notion.setFournisseurIa(ia.fournisseur());
            notion.setVersionPrompt(ia.versionPrompt());
            notion.setRattrapageJson(rattrapageValide(ia.questionRattrapage()) ? json(ia.questionRattrapage()) : null);
            alertes = ia.alertes() == null ? List.of() : ia.alertes();
        }
        return reponse(notion, alertes);
    }

    @Transactional
    public RattrapageResultat rattrapage(Long notionRateeId, RattrapageRequest request, CurrentUser user) {
        NotionRatee notion = notionRateeRepository.findById(notionRateeId)
                .orElseThrow(() -> new ResourceNotFoundException("Remédiation", notionRateeId));
        user.requireOwnerOrAdmin(notion.getEtudiantId());
        QuestionIaDto question = lireRattrapage(notion);
        if (question == null) {
            throw new BadRequestException("Pas de question de rattrapage pour cette erreur : demande d'abord l'explication");
        }
        if (request.indexChoix() >= question.choix().size()) {
            throw new BadRequestException("Choix inconnu");
        }
        boolean correcte = question.choix().get(request.indexChoix()).estCorrecte();
        if (correcte) {
            notion.setResolue(true);
        }
        int bonne = 0;
        while (!question.choix().get(bonne).estCorrecte()) {
            bonne++;
        }
        return new RattrapageResultat(correcte, bonne, question.explication());
    }

    private RemedierIaDto appelerIa(NotionRatee notion, String authorization) {
        Reponse reponse = notion.getReponse();
        Question q = reponse.getQuestion();
        Option choisie = reponse.getOptionChoisie();
        RemedierIaRequestDto corps = new RemedierIaRequestDto(notion.getNotion(),
                q.getNiveauBloom() != null ? q.getNiveauBloom() : BLOOM_PAR_DEFAUT, q.getEnonce(),
                choisie != null ? choisie.getTexte() : SANS_REPONSE, q.bonneOption().getTexte(),
                choisie != null ? choisie.getErreurAssociee() : null, q.getExplication());
        ApiResponse<RemedierIaDto> resultat;
        try {
            resultat = iaClient.remedier(authorization, corps);
        } catch (FeignException e) {
            throw new ServiceUnavailableException("Le service d'IA est injoignable, réessaie dans un instant.");
        }
        if (resultat == null || resultat.data() == null) {
            throw new ServiceUnavailableException("Le service d'IA n'a pas renvoyé de remédiation.");
        }
        return resultat.data();
    }

    /** Une question de rattrapage n'est gardée que si elle est corrigeable : au moins 2 choix, une seule bonne réponse. */
    private static boolean rattrapageValide(QuestionIaDto q) {
        return q != null && q.enonce() != null && q.choix() != null && q.choix().size() >= 2
                && q.choix().stream().filter(ChoixIaDto::estCorrecte).count() == 1;
    }

    private RemediationResponse reponse(NotionRatee n, List<String> alertes) {
        QuestionIaDto q = lireRattrapage(n);
        QuestionRattrapage rattrapage = q == null ? null
                : new QuestionRattrapage(q.enonce(), q.choix().stream().map(ChoixIaDto::texte).toList());
        return new RemediationResponse(n.getId(), n.getNotion(), n.getDiagnostic(), n.getExplicationIa(), n.getConseil(),
                rattrapage, n.isResolue(), n.getFournisseurIa(), alertes);
    }

    private String json(QuestionIaDto q) {
        try {
            return mapper.writeValueAsString(q);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private QuestionIaDto lireRattrapage(NotionRatee n) {
        if (n.getRattrapageJson() == null) {
            return null;
        }
        try {
            return mapper.readValue(n.getRattrapageJson(), QuestionIaDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

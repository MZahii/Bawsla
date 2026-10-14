package tn.esprit.bawsla.quiz.service;

import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.quiz.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.quiz.common.security.CurrentUser;
import tn.esprit.bawsla.quiz.dto.CertificatResponse;
import tn.esprit.bawsla.quiz.entity.Certificat;
import tn.esprit.bawsla.quiz.entity.Quiz;
import tn.esprit.bawsla.quiz.repository.CertificatRepository;

/** Certificats délivrés à la réussite de l'examen final, et leur vérification par numéro. */
@Service
@Transactional(readOnly = true)
public class CertificatService {

    private final CertificatRepository certificatRepository;

    public CertificatService(CertificatRepository certificatRepository) {
        this.certificatRepository = certificatRepository;
    }

    /** Délivre le certificat du cours (un seul par apprenant et par cours) et renvoie son numéro. */
    @Transactional
    public String delivrer(Long etudiantId, Quiz examen, int score) {
        String numero;
        do {
            numero = "BW-" + Year.now().getValue() + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        } while (certificatRepository.findByNumero(numero).isPresent());
        certificatRepository.save(new Certificat(numero, etudiantId, examen, score));
        return numero;
    }

    public List<CertificatResponse> mesCertificats(CurrentUser user) {
        return certificatRepository.findByEtudiantIdOrderByDateEmissionDesc(user.id()).stream()
                .map(CertificatResponse::from).toList();
    }

    /** Tous les certificats émis : back-office admin. */
    public List<CertificatResponse> tous(CurrentUser user) {
        user.requireAnyRole(CurrentUser.ADMIN);
        return certificatRepository.findAllByOrderByDateEmissionDesc().stream().map(CertificatResponse::from).toList();
    }

    /** Vérification d'un numéro par un tiers (recruteur…) : sans l'identifiant de l'apprenant. */
    public CertificatResponse verifier(String numero) {
        return certificatRepository.findByNumero(numero.strip().toUpperCase(Locale.ROOT))
                .map(CertificatResponse::verification)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun certificat ne porte le numéro " + numero));
    }
}

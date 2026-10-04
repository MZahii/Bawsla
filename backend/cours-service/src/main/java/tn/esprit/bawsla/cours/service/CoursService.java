package tn.esprit.bawsla.cours.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.cours.common.security.CurrentUser;
import tn.esprit.bawsla.cours.dto.CoursRequest;
import tn.esprit.bawsla.cours.dto.CoursResponse;
import tn.esprit.bawsla.cours.dto.CoursTexteResponse;
import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.repository.CoursRepository;

@Service
@Transactional(readOnly = true)
public class CoursService {

    private final CoursRepository coursRepository;

    public CoursService(CoursRepository coursRepository) {
        this.coursRepository = coursRepository;
    }

    public List<CoursResponse> findAll(String categorie, Niveau niveau) {
        return coursRepository.search(categorie, niveau).stream().map(CoursResponse::from).toList();
    }

    public CoursResponse findById(Long id) {
        return CoursResponse.from(getOrThrow(id));
    }

    @Transactional
    public CoursResponse create(CoursRequest request, CurrentUser user) {
        user.requireAnyRole(CurrentUser.ENSEIGNANT, CurrentUser.ADMIN);
        Cours cours = new Cours();
        apply(cours, request);
        cours.setEnseignantId(user.id());
        return CoursResponse.from(coursRepository.save(cours));
    }

    @Transactional
    public CoursResponse update(Long id, CoursRequest request, CurrentUser user) {
        Cours cours = getOrThrow(id);
        user.requireOwnerOrAdmin(cours.getEnseignantId());
        apply(cours, request);
        return CoursResponse.from(cours);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        Cours cours = getOrThrow(id);
        user.requireOwnerOrAdmin(cours.getEnseignantId());
        coursRepository.delete(cours);
    }

    /** STUB : l'extraction du texte PDF sera implémentée par le module Cours. */
    public CoursTexteResponse getTexte(Long id) {
        Cours cours = getOrThrow(id);
        String texte = "[STUB] Texte extrait non disponible. Titre : " + cours.getTitre()
                + (cours.getDescription() != null ? ". " + cours.getDescription() : "");
        return new CoursTexteResponse(cours.getId(), texte, true);
    }

    private Cours getOrThrow(Long id) {
        return coursRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cours", id));
    }

    private static void apply(Cours cours, CoursRequest r) {
        cours.setTitre(r.titre());
        cours.setDescription(r.description());
        cours.setCategorie(r.categorie());
        cours.setNiveau(r.niveau());
        cours.setFichierPdf(r.fichierPdf());
    }
}

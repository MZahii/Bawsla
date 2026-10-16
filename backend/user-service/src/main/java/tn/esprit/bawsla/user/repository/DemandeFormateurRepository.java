package tn.esprit.bawsla.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.user.entity.DemandeFormateur;
import tn.esprit.bawsla.user.entity.StatutDemande;

public interface DemandeFormateurRepository extends JpaRepository<DemandeFormateur, Long> {

    /** Toutes les demandes avec leur demandeur (une seule requête), les plus récentes d'abord. */
    @EntityGraph(attributePaths = "utilisateur")
    List<DemandeFormateur> findAllByOrderByDateCreationDesc();

    @EntityGraph(attributePaths = "utilisateur")
    Optional<DemandeFormateur> findFirstByUtilisateurIdOrderByDateCreationDesc(Long utilisateurId);

    boolean existsByUtilisateurIdAndStatut(Long utilisateurId, StatutDemande statut);
}

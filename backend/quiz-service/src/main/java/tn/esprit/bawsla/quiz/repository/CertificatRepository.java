package tn.esprit.bawsla.quiz.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.quiz.entity.Certificat;

public interface CertificatRepository extends JpaRepository<Certificat, Long> {

    List<Certificat> findByEtudiantIdOrderByDateEmissionDesc(Long etudiantId);

    List<Certificat> findAllByOrderByDateEmissionDesc();

    Optional<Certificat> findByNumero(String numero);

    boolean existsByEtudiantIdAndCoursId(Long etudiantId, Long coursId);

    Optional<Certificat> findByEtudiantIdAndCoursId(Long etudiantId, Long coursId);
}
